package com.twitter.util.registry

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SimpleRegistryTest extends RegistryTest {
  def mkRegistry(): Registry = new SimpleRegistry
  def name: String = "SimpleRegistry"
}
