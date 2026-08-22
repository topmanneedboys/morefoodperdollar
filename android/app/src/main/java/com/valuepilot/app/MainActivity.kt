package com.valuepilot.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private var shellState =
        AppShellState.initial()

    private lateinit var shellRoot: View
    private lateinit var bottomNavArea: View

    private lateinit var bottomNavigation:
        BottomNavigationView

    private lateinit var screenEyebrow:
        TextView

    private lateinit var screenTitle:
        TextView

    private lateinit var screenBody:
        TextView

    private lateinit var screenFootnote:
        TextView

    private lateinit var primaryAction:
        Button

    private var comparisonActivityOpen =
        false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_shell
        )

        shellRoot =
            findViewById(
                R.id.shellRoot
            )

        bottomNavArea =
            findViewById(
                R.id.bottomNavArea
            )

        bottomNavigation =
            findViewById(
                R.id.bottomNavigation
            )

        screenEyebrow =
            findViewById(
                R.id.screenEyebrow
            )

        screenTitle =
            findViewById(
                R.id.screenTitle
            )

        screenBody =
            findViewById(
                R.id.screenBody
            )

        screenFootnote =
            findViewById(
                R.id.screenFootnote
            )

        primaryAction =
            findViewById(
                R.id.primaryAction
            )

        installSystemBarInsets()

        shellState =
            restoreShellState(
                savedInstanceState
            )

        bottomNavigation
            .setOnItemSelectedListener { item ->
                val tab =
                    when (item.itemId) {
                        R.id.navHome ->
                            AppPrimaryTab.HOME

                        R.id.navSearch ->
                            AppPrimaryTab.SEARCH

                        R.id.navBasket ->
                            AppPrimaryTab.BASKET

                        R.id.navSaved ->
                            AppPrimaryTab.SAVED

                        else ->
                            null
                    }
                        ?: return@setOnItemSelectedListener false

                dispatch(
                    AppShellIntent.SelectPrimary(
                        tab
                    )
                )

                true
            }

        primaryAction.setOnClickListener {
            openComparison()
        }

        bottomNavigation.selectedItemId =
            menuIdFor(
                shellState.selectedPrimaryTab
            )

        renderShell(
            shellState
        )
    }

    override fun onResume() {
        super.onResume()

        if (
            comparisonActivityOpen &&
            shellState.route ==
                AppRoute.COMPARE
        ) {
            comparisonActivityOpen = false

            dispatch(
                AppShellIntent.NavigateBack
            )
        }
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        outState.putString(
            STATE_PRIMARY_TAB,
            shellState
                .selectedPrimaryTab
                .name
        )

        super.onSaveInstanceState(
            outState
        )
    }

    private fun installSystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
            shellRoot
        ) { view, insets ->

            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                0
            )

            bottomNavArea.setPadding(
                0,
                0,
                0,
                bars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(
            shellRoot
        )
    }

    private fun restoreShellState(
        savedInstanceState: Bundle?
    ): AppShellState {
        val savedName =
            savedInstanceState
                ?.getString(
                    STATE_PRIMARY_TAB
                )

        val savedTab =
            savedName
                ?.let {
                    runCatching {
                        AppPrimaryTab.valueOf(it)
                    }.getOrNull()
                }
                ?: AppPrimaryTab.HOME

        return AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(
                savedTab
            )
        )
    }

    private fun openComparison() {
        shellState =
            AppShellReducer.reduce(
                shellState,
                AppShellIntent
                    .OpenStandaloneCompare
            )

        comparisonActivityOpen = true

        startActivity(
            Intent(
                this,
                ComparisonActivity::class.java
            )
        )
    }

    private fun dispatch(
        intent: AppShellIntent
    ) {
        val next =
            AppShellReducer.reduce(
                shellState,
                intent
            )

        if (next == shellState) {
            return
        }

        shellState = next

        renderShell(
            next
        )
    }

    private fun renderShell(
        state: AppShellState
    ) {
        if (
            state.route ==
                AppRoute.COMPARE
        ) {
            return
        }

        val copy =
            copyFor(
                state.selectedPrimaryTab
            )

        screenEyebrow.text =
            copy.eyebrow

        screenTitle.text =
            copy.title

        screenBody.text =
            copy.body

        screenFootnote.text =
            copy.footnote

        primaryAction.visibility =
            if (copy.showCompareAction) {
                View.VISIBLE
            } else {
                View.GONE
            }

        val expectedMenuItem =
            menuIdFor(
                state.selectedPrimaryTab
            )

        if (
            bottomNavigation.selectedItemId !=
            expectedMenuItem
        ) {
            bottomNavigation.selectedItemId =
                expectedMenuItem
        }
    }

    private fun copyFor(
        tab: AppPrimaryTab
    ): ScreenCopy =
        when (tab) {
            AppPrimaryTab.HOME ->
                ScreenCopy(
                    eyebrow =
                        getString(
                            R.string.home_eyebrow
                        ),
                    title =
                        getString(
                            R.string.home_title
                        ),
                    body =
                        getString(
                            R.string.home_body
                        ),
                    footnote =
                        getString(
                            R.string.home_footnote
                        ),
                    showCompareAction = true
                )

            AppPrimaryTab.SEARCH ->
                ScreenCopy(
                    eyebrow =
                        getString(
                            R.string.search_eyebrow
                        ),
                    title =
                        getString(
                            R.string.search_title
                        ),
                    body =
                        getString(
                            R.string.search_body
                        ),
                    footnote =
                        getString(
                            R.string.search_footnote
                        ),
                    showCompareAction = false
                )

            AppPrimaryTab.BASKET ->
                ScreenCopy(
                    eyebrow =
                        getString(
                            R.string.basket_eyebrow
                        ),
                    title =
                        getString(
                            R.string.basket_title
                        ),
                    body =
                        getString(
                            R.string.basket_body
                        ),
                    footnote =
                        getString(
                            R.string.basket_footnote
                        ),
                    showCompareAction = false
                )

            AppPrimaryTab.SAVED ->
                ScreenCopy(
                    eyebrow =
                        getString(
                            R.string.saved_eyebrow
                        ),
                    title =
                        getString(
                            R.string.saved_title
                        ),
                    body =
                        getString(
                            R.string.saved_body
                        ),
                    footnote =
                        getString(
                            R.string.saved_footnote
                        ),
                    showCompareAction = false
                )
        }

    private fun menuIdFor(
        tab: AppPrimaryTab
    ): Int =
        when (tab) {
            AppPrimaryTab.HOME ->
                R.id.navHome

            AppPrimaryTab.SEARCH ->
                R.id.navSearch

            AppPrimaryTab.BASKET ->
                R.id.navBasket

            AppPrimaryTab.SAVED ->
                R.id.navSaved
        }

    private data class ScreenCopy(
        val eyebrow: String,
        val title: String,
        val body: String,
        val footnote: String,
        val showCompareAction: Boolean
    )

    companion object {
        private const val STATE_PRIMARY_TAB =
            "app_shell.primary_tab"
    }
}
