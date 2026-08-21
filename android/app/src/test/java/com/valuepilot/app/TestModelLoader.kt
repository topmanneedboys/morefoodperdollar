package com.valuepilot.app

import java.io.File

internal object TestModelLoader {
    fun load() {
        val model = listOf(
            File("app/src/main/assets/local_ai_model.json"),
            File("src/main/assets/local_ai_model.json"),
            File("android/app/src/main/assets/local_ai_model.json")
        ).firstOrNull(File::isFile) ?: error("local AI model asset not found")
        LocalFoodModel.initializeFromJson(model.readText())
    }
}
