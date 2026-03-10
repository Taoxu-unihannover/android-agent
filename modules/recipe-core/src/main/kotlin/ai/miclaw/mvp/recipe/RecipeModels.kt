package ai.miclaw.mvp.recipe

import ai.miclaw.mvp.models.Observation

data class RecipeStep(
  val id: String,
  val toolName: String,
  val args: Map<String, Any?> = emptyMap(),
)

data class Recipe(
  val id: String,
  val name: String,
  val matchKeywords: List<String>,
  val steps: List<RecipeStep>,
)

data class RecipeMatchResult(
  val recipe: Recipe,
  val confidence: Double,
  val reason: String,
)

interface RecipeRegistry {
  fun match(goal: String, observations: List<Observation>): RecipeMatchResult?
}

class StaticRecipeRegistry(
  private val recipes: List<Recipe>,
) : RecipeRegistry {
  override fun match(goal: String, observations: List<Observation>): RecipeMatchResult? {
    val normalizedGoal = goal.lowercase()
    val candidate = recipes.firstOrNull { recipe ->
      recipe.matchKeywords.any { normalizedGoal.contains(it.lowercase()) }
    } ?: return null
    return RecipeMatchResult(
      recipe = candidate,
      confidence = 0.8,
      reason = "matched by keyword",
    )
  }
}
