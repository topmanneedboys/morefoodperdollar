package com.valuepilot.app

import android.os.Bundle
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.valuepilot.core.CompareHerePriceSelection

/**
 * First-class single-product price question. All recognition and exact math stay in the
 * provider-neutral coordinator; this Activity only owns bounded input and local persistence.
 */
class GoodPriceActivity : AppCompatActivity() {
    private lateinit var productInput: TextInputEditText
    private lateinit var priceSelectionGroup: RadioGroup
    private lateinit var checkButton: Button
    private lateinit var screen: GoodPriceCheckSurfaceView
    private lateinit var presenter: GoodPriceCheckScreenPresenter
    private lateinit var memoryStatus: android.widget.TextView
    private lateinit var clearMemoryButton: Button
    private lateinit var memoryStore: CompareHerePrivatePriceMemoryStore

    private var priceSelection = CompareHerePriceSelection.CURRENT
    private var privateMemory = CompareHerePrivatePriceMemoryState.empty()
    private var privateMemoryLoadIssue: CompareHerePrivatePriceMemoryStoreIssue? = null
    private var restoring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_good_price)

        productInput = findViewById(R.id.goodPriceInput)
        priceSelectionGroup = findViewById(R.id.goodPriceSelectionGroup)
        checkButton = findViewById(R.id.checkGoodPriceButton)
        screen = findViewById(R.id.goodPriceScreen)
        presenter = GoodPriceCheckScreenPresenter(screen)
        memoryStatus = findViewById(R.id.goodPriceMemoryStatus)
        clearMemoryButton = findViewById(R.id.clearGoodPriceMemoryButton)
        memoryStore = CompareHerePrivatePriceMemoryAndroidStore(this)

        privateMemory = loadPrivateMemory()

        productInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (!restoring) renderIdle()
                }
            }
        )

        priceSelectionGroup.setOnCheckedChangeListener { _, checkedId ->
            if (restoring) return@setOnCheckedChangeListener
            priceSelection =
                when (checkedId) {
                    R.id.goodPriceSelectionMember -> CompareHerePriceSelection.MEMBER
                    else -> CompareHerePriceSelection.CURRENT
                }
            renderIdle()
        }

        checkButton.setOnClickListener { runCheck() }
        findViewById<Button>(R.id.clearGoodPriceButton).setOnClickListener {
            restoring = true
            productInput.setText("")
            priceSelection = CompareHerePriceSelection.CURRENT
            priceSelectionGroup.check(R.id.goodPriceSelectionCurrent)
            restoring = false
            renderIdle()
        }
        clearMemoryButton.setOnClickListener {
            val result = memoryStore.clear()
            if (result.accepted) {
                privateMemory = CompareHerePrivatePriceMemoryState.empty()
                privateMemoryLoadIssue = null
                hideMemoryStatus()
            } else {
                memoryStatus.text = getString(R.string.compare_memory_clear_error)
                memoryStatus.setTextColor(Color.parseColor("#92400E"))
                memoryStatus.visibility = View.VISIBLE
                clearMemoryButton.visibility = View.VISIBLE
            }
        }

        priceSelection =
            savedInstanceState?.getString(STATE_PRICE_SELECTION)?.let {
                CompareHerePriceSelectionPersistence.decode(it)
            } ?: CompareHerePriceSelection.CURRENT
        restoring = true
        priceSelectionGroup.check(
            if (priceSelection == CompareHerePriceSelection.MEMBER) {
                R.id.goodPriceSelectionMember
            } else {
                R.id.goodPriceSelectionCurrent
            }
        )
        restoring = false
        renderIdle()
    }

    override fun onResume() {
        super.onResume()
        privateMemory = loadPrivateMemory()
        if (privateMemoryLoadIssue != null) showMemoryUnavailable()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            STATE_PRICE_SELECTION,
            CompareHerePriceSelectionPersistence.encode(priceSelection)
        )
        super.onSaveInstanceState(outState)
    }

    private fun runCheck() {
        val evaluation =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = productInput.text?.toString().orEmpty(),
                observedAtEpochMillis = System.currentTimeMillis(),
                priceSelection = priceSelection,
                privateMemory = privateMemory
            )
        presenter.render(evaluation.state)

        val capture = evaluation.privateMemoryCapture
        if (capture == null) {
            if (privateMemoryLoadIssue == null) hideMemoryStatus() else showMemoryUnavailable()
            return
        }

        val saved = memoryStore.append(capture)
        if (saved.accepted) {
            privateMemory = requireNotNull(saved.state)
            privateMemoryLoadIssue = null
            memoryStatus.text =
                getString(
                    R.string.good_price_memory_saved,
                    capture.entries.single().displayName
                )
            memoryStatus.setTextColor(Color.parseColor("#047857"))
            memoryStatus.visibility = View.VISIBLE
            clearMemoryButton.visibility = View.VISIBLE
        } else {
            if (privateMemoryLoadIssue != null ||
                saved.issue == CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_INVALID
            ) {
                privateMemoryLoadIssue = saved.issue ?: privateMemoryLoadIssue
                showMemoryUnavailable()
            } else {
                memoryStatus.text = getString(R.string.good_price_memory_error)
                memoryStatus.setTextColor(Color.parseColor("#92400E"))
                memoryStatus.visibility = View.VISIBLE
                clearMemoryButton.visibility = View.VISIBLE
            }
        }
    }

    private fun renderIdle() {
        if (privateMemoryLoadIssue == null) hideMemoryStatus() else showMemoryUnavailable()
        checkButton.isEnabled = productInput.text?.toString()?.isNotBlank() == true
        presenter.render(
            GoodPriceCheckRouteState(
                status = GoodPriceCheckRouteStatus.NEEDS_PRODUCT,
                title = "Ready to check a price",
                guidance = "Enter one product, then tap Check this price."
            )
        )
    }

    private fun loadPrivateMemory(): CompareHerePrivatePriceMemoryState {
        val loaded = memoryStore.load()
        privateMemoryLoadIssue = loaded.issue
        return loaded.state ?: CompareHerePrivatePriceMemoryState.empty()
    }

    private fun showMemoryUnavailable() {
        memoryStatus.text = getString(R.string.good_price_memory_unavailable)
        memoryStatus.setTextColor(Color.parseColor("#92400E"))
        memoryStatus.visibility = View.VISIBLE
        clearMemoryButton.visibility = View.VISIBLE
    }

    private fun hideMemoryStatus() {
        memoryStatus.text = ""
        memoryStatus.visibility = View.GONE
        clearMemoryButton.visibility = View.GONE
    }

    companion object {
        private const val STATE_PRICE_SELECTION = "good_price.price_selection"
    }
}
