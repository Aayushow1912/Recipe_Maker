package com.example.recipeapp

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.Toast
import com.example.recipeapp.databinding.ActivitySearchBinding

class SearchActivity : AppCompatActivity() {
    private lateinit var binding:ActivitySearchBinding
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.search.requestFocus()
        binding.rvSearch.visibility = View.GONE

        binding.goBackHome.setOnClickListener{
            finish()
        }

        binding.search.setOnEditorActionListener { _, actionId, event ->
            val isImeAction =
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_GO

            val isEnterKey =
                event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN

            if (isImeAction || isEnterKey) {
                val query = binding.search.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideKeyboard()
                    askAiForRecipe(query)
                } else {
                    Toast.makeText(this, "Please enter recipe text first.", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                false
            }
        }
    }

    private fun askAiForRecipe(recipeText: String) {
        if (isLoading) return
        setLoadingState(true)
        Toast.makeText(this, "Getting AI response...", Toast.LENGTH_SHORT).show()
        val openAiClient = OpenAiClient(BuildConfig.OPENROUTER_API_KEY)

        Thread {
            openAiClient.getRecipeResponse(
                userRecipeText = recipeText,
                onSuccess = { aiResponse ->
                    runOnUiThread {
                        setLoadingState(false)
                        openRecipeActivityFromAiResponse(aiResponse)
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        setLoadingState(false)
                        Log.e("SearchActivity", "OpenRouter error: $error")
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }.start()
    }

    private fun openRecipeActivityFromAiResponse(aiResponse: LlmRecipeResponse) {
        val title = aiResponse.title.ifBlank { "AI Recipe" }
        val time = aiResponse.time.ifBlank { "Time not specified" }
        val ingredientsList = aiResponse.ingredients.ifEmpty { listOf("No ingredients provided") }
        val stepsText = aiResponse.steps
            .ifEmpty { listOf("No steps provided") }
            .mapIndexed { index, step -> "${index + 1}. $step" }
            .joinToString("\n\n")

        val ingPayload = buildString {
            append(time)
            for (item in ingredientsList) {
                append("\n")
                append(item)
            }
        }

        val intent = Intent(this, RecipeActivity::class.java).apply {
            putExtra("tittle", title)
            putExtra("des", stepsText)
            putExtra("ing", ingPayload)
            putExtra("img", aiResponse.imageUrl)
        }
        startActivity(intent)
    }

    private fun setLoadingState(loading: Boolean) {
        isLoading = loading
        binding.loadingBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.search.isEnabled = !loading
        binding.goBackHome.isEnabled = !loading
    }

    private fun hideKeyboard() {
        val inputManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(binding.search.windowToken, 0)
    }
}
