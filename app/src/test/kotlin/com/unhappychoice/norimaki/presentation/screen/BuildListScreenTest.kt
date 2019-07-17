package com.unhappychoice.norimaki.presentation.screen

import com.unhappychoice.norimaki.R
import com.winterbe.expekt.expect
import io.polymorphicpanda.kspec.KSpec
import io.polymorphicpanda.kspec.describe
import io.polymorphicpanda.kspec.it
import io.polymorphicpanda.kspec.junit.JUnitKSpecRunner
import org.junit.runner.RunWith

@RunWith(JUnitKSpecRunner::class)
class BuildListScreenTest : KSpec() {
    lateinit var subject: BuildListScreen

    override fun spec() {
        beforeEach {
            subject = BuildListScreen("")
        }

        describe("BuildListScreen") {
            describe(".getTitle()") {
                it("should return title") {
                    expect(BuildListScreen("").getTitle()).to.equal("Recent Builds")
                }
                it("should return title") {
                    expect(BuildListScreen("unhappychoice/Norimaki").getTitle()).to.equal("unhappychoice/Norimaki")
                }
            }
            describe(".getLayoutResource()") {
                it("should return view resource") {
                    expect(subject.getLayoutResource()).to.equal(R.layout.build_list_view)
                }
            }
        }
    }
}


