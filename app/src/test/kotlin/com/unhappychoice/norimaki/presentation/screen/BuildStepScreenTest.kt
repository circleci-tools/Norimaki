package com.unhappychoice.norimaki.presentation.screen

import com.github.unhappychoice.circleci.v1.response.Build
import com.github.unhappychoice.circleci.v1.response.BuildStep
import io.mockk.every
import io.mockk.mockk
import com.unhappychoice.norimaki.R
import com.winterbe.expekt.expect
import io.polymorphicpanda.kspec.KSpec
import io.polymorphicpanda.kspec.describe
import io.polymorphicpanda.kspec.it
import io.polymorphicpanda.kspec.junit.JUnitKSpecRunner
import org.junit.runner.RunWith

@RunWith(JUnitKSpecRunner::class)
class BuildStepScreenTest : KSpec() {
    lateinit var subject: BuildStepScreen
    lateinit var build: Build
    lateinit var buildStep: BuildStep
    val stepIndex = 0

    override fun spec() {
        beforeEach {
            build = mockk(relaxed = true)
            buildStep = mockk { every { name } returns "build name" }
            subject = BuildStepScreen(build, buildStep)
        }

        describe("BuildStepScreen") {
            describe(".getTitle()") {
                it("should return title") {
                    expect(subject.getTitle()).to.equal(buildStep.name)
                }
            }
            describe(".getLayoutResource()") {
                it("should return view resource") {
                    expect(subject.getLayoutResource()).to.equal(R.layout.build_step_view)
                }
            }
        }
    }
}
