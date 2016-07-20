package com.unhappychoice.norimaki.extension

import rx.Observable
import rx.lang.kotlin.BehaviorSubject
import rx.subjects.BehaviorSubject
import kotlin.reflect.KProperty

class Variable<T>(value: T) {
  private var _value: T = value
  private val _subject: BehaviorSubject<T> = BehaviorSubject(value)

  var value: T by object {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = _value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
      _value = value
      _subject.onNext(value)
    }
  }

  fun asObservable(): Observable<T> = _subject

  fun finalize() = _subject.onCompleted()
}