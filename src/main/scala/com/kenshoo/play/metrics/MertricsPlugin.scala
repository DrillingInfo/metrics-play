/*
* Copyright 2013 Kenshoo.com
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.kenshoo.play.metrics

import java.util.concurrent.TimeUnit

import play.api.{Application, Play, Plugin}

import com.codahale.metrics.{MetricSet, MetricRegistry, SharedMetricRegistries}
import com.codahale.metrics.json.MetricsModule
import com.codahale.metrics.jvm.{ThreadStatesGaugeSet, GarbageCollectorMetricSet, MemoryUsageGaugeSet}
import collection.JavaConversions._

import com.fasterxml.jackson.databind.ObjectMapper


object MetricsRegistry {
  def default = Play.current.plugin[MetricsPlugin] match {
      case Some(plugin) => SharedMetricRegistries.getOrCreate(plugin.registryName)
      case None => throw new Exception("metrics plugin is not configured")
  }
}


class MetricsPlugin(val app: Application) extends Plugin {
  val validUnits = Some(Set("NANOSECONDS", "MICROSECONDS", "MILLISECONDS", "SECONDS", "MINUTES", "HOURS", "DAYS"))

  val mapper: ObjectMapper = new ObjectMapper()

  def registryName = app.configuration.getString("metrics.name").getOrElse("default")
  def rateUnit     = app.configuration.getString("metrics.rateUnit", validUnits).getOrElse("SECONDS")
  def durationUnit = app.configuration.getString("metrics.durationUnit", validUnits).getOrElse("SECONDS")
  def showSamples  = app.configuration.getBoolean("metrics.showSamples").getOrElse(false)

  implicit def stringToTimeUnit(s: String) : TimeUnit = TimeUnit.valueOf(s)

  override def onStart() {
    if (enabled) {
      val registry: MetricRegistry = SharedMetricRegistries.getOrCreate(registryName)
      val jvmMetricsEnabled = app.configuration.getBoolean("metrics.jvm").getOrElse(true)
      if (jvmMetricsEnabled) {
        registerAll("gs_stats", new GarbageCollectorMetricSet(), registry)
        registerAll("mem_stats", new MemoryUsageGaugeSet(), registry)
        registerAll("thread_stats", new ThreadStatesGaugeSet(), registry)
      }
      val module = new MetricsModule(rateUnit, durationUnit, showSamples)
      mapper.registerModule(module)
    }
  }


  override def onStop() {
    if (enabled) {
      SharedMetricRegistries.remove(registryName)
    }
  }

  def registerAll(prefix: String , metricSet: MetricSet , registry: MetricRegistry) {
    for (entry <- metricSet.getMetrics().entrySet()) {
      registry.register(prefix + "." + entry.getKey(), entry.getValue());
    }
  }

  override def enabled = app.configuration.getBoolean("metrics.enabled").getOrElse(true)
}

