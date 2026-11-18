package com.unhappychoice.norimaki.domain.model

import com.github.unhappychoice.circleci.v1.response.BuildStep

data class BuildStepId(val number: Int?, val position: Int? = null, val legacyName: String? = null) {
    fun resolve(steps: List<BuildStep>?): BuildStep? = when {
        number != null -> steps?.firstOrNull { it.step() == number }
        position != null -> steps?.getOrNull(position)
        else -> legacyName?.let { steps?.findByName(it) }
    }

    companion object {
        fun from(step: BuildStep, position: Int): BuildStepId =
            step.step()?.let { BuildStepId(it) }
                ?: BuildStepId(null, position.takeIf { it >= 0 }, step.name.takeIf { position < 0 })
    }
}
