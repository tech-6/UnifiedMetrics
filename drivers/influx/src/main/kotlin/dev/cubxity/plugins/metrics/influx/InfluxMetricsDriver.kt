/*
 *     UnifiedMetrics is a fully-featured metrics collection plugin for Minecraft servers.
 *     Copyright (C) 2021  Cubxity
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published
 *     by the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.cubxity.plugins.metrics.influx

import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.WriteApi
import com.uchuhimo.konf.Config
import dev.cubxity.plugins.metrics.api.UnifiedMetrics
import dev.cubxity.plugins.metrics.api.metric.MetricsDriver
import dev.cubxity.plugins.metrics.api.metric.data.Point
import dev.cubxity.plugins.metrics.influx.config.InfluxSpec
import java.util.concurrent.TimeUnit
import com.influxdb.client.write.Point as InfluxPoint

class InfluxMetricsDriver(private val api: UnifiedMetrics, private val config: Config) : MetricsDriver {
    private var influxDBClient: InfluxDBClient? = null
    private var writeApi: WriteApi? = null

    override fun connect() {
        val options = InfluxDBClientOptions.builder()
            .url(config[InfluxSpec.url])
            .authenticate(
                config[InfluxSpec.username],
                config[InfluxSpec.password].toCharArray()
            )
            .bucket(config[InfluxSpec.bucket])
            .org("-")
            .build()

        influxDBClient = InfluxDBClientFactory.create(options)
        writeApi = influxDBClient?.writeApi
    }

    override fun scheduleTasks() {
        val interval = config[InfluxSpec.interval]

        val scheduler = api.scheduler

        scheduler.asyncRepeating({
            // Async
            writePoints(api.metricsManager.serializeMetrics(false))

            // Sync
            scheduler.sync.execute {
                writePoints(api.metricsManager.serializeMetrics(true))
            }
        }, interval, TimeUnit.SECONDS)
    }

    override fun close() {
        writeApi?.close()
        influxDBClient?.close()
        influxDBClient = null
    }

    private fun writePoints(points: List<Point>) {
        val api = writeApi ?: return
        for (point in points) {
            val influxPoint = InfluxPoint(point.name)
            influxPoint.addFields(point.fields)
            influxPoint.addTags(point.tags)

            api.writePoint(influxPoint)
        }
    }
}