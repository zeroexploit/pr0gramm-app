package com.pr0gramm.app.ui.intro

import com.github.appintro.AppIntroBaseFragment
import com.github.appintro.AppIntroFragment
import com.pr0gramm.app.R

class GenericAppIntroFragment : AppIntroBaseFragment() {
    override val layoutId: Int = R.layout.intro_fragment_generic

    companion object {
        fun newInstance(title: CharSequence, description: CharSequence, imageDrawable: Int, bgColorRes: Int,
                        titleColorRes: Int = 0, descColorRes: Int = 0): GenericAppIntroFragment {

            val slide = GenericAppIntroFragment()
            slide.arguments = AppIntroFragment.createInstance(
                title = title,
                description = description,
                imageDrawable = imageDrawable,
                backgroundColorRes = bgColorRes,
                titleColorRes = titleColorRes,
                descriptionColorRes = descColorRes
            ).arguments

            return slide
        }
    }
}
