package com.unhappychoice.norimaki

import android.content.Context

class DaggerService {
  companion object {
    val SERVICE_NAME = "dagger_service"
  }

  fun inject(obj: Any, context: Context, key: Any?) {
    val componentImplClassName = StringBuilder()
      .append(obj.javaClass.`package`.name)
      .append(".Dagger")
      .append(obj.javaClass.simpleName.replace("View", "Screen"))
      .append("_Component").toString()
    val componentImplClass = Class.forName(componentImplClassName)
    val builderMethod = componentImplClass.getMethod("builder")
    val injectMethod = componentImplClass.getMethod("inject", obj.javaClass)

    val builderClass = Class.forName(componentImplClassName + "\$Builder")
    val componentMethod = builderClass.getMethod("component", MainActivity.Component::class.java)
    val buildMethod = builderClass.getMethod("build")

    val builder = builderMethod.invoke(null)
    componentMethod.invoke(builder, getActivityComponent(context))

    if (key is HasModule) {
      val screenClassName = obj.javaClass.name.replace("View", "Screen")
      val moduleMethod = builderClass.getMethod("module", Class.forName(screenClassName + "\$Module"))
      moduleMethod.invoke(builder, key.getModule())
    }

    val component = buildMethod.invoke(builder)
    injectMethod.invoke(component, obj)
  }

  private fun getActivityComponent(context: Context): MainActivity.Component {
    return context.getSystemService(MainActivity.SERVICE_NAME) as MainActivity.Component
  }
}