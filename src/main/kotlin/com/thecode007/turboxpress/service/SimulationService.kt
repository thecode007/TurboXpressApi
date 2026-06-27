package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.repository.DriverProfileRepository
import com.thecode007.turboxpress.routing.RoutingService
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future

@Service
class SimulationService(
    private val routingService: RoutingService,
    private val driverProfileRepository: DriverProfileRepository,
    private val transactionTemplate: TransactionTemplate
) {
    private val executor = Executors.newCachedThreadPool()
    private var simulationJobs = mutableMapOf<Long, Future<*>>()

    fun simulateDriverMovement(
        orderId: Long,
        driverId: UUID,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        durationSeconds: Int = 120
    ) {
        simulationJobs[orderId]?.cancel(true)

        simulationJobs[orderId] = executor.submit {
            try {
                println("Starting simulation for order $orderId from ($startLat, $startLng) to ($endLat, $endLng)")
                val route = routingService.getRoute(startLat, startLng, endLat, endLng)
                val points = route.points
                if (points.isEmpty()) return@submit

                val intervalMs = (durationSeconds * 1000) / points.size.coerceAtLeast(1)
                
                println("Simulation interval: ${intervalMs}ms, Points: ${points.size}")

                for (point in points) {
                    if (Thread.currentThread().isInterrupted) return@submit
                    Thread.sleep(intervalMs.toLong())
                    updateDriverLocation(driverId, point.lat, point.lng)
                }
                println("Simulation finished for order $orderId")
            } catch (e: InterruptedException) {
                // Cancelled
            } catch (e: Exception) {
                println("Simulation error for order $orderId: ${e.message}")
            }
        }
    }
    
    fun updateDriverLocation(driverId: UUID, lat: Double, lng: Double) {
        transactionTemplate.execute {
            driverProfileRepository.findById(driverId).ifPresent { driver ->
                val location = org.locationtech.jts.geom.GeometryFactory().createPoint(org.locationtech.jts.geom.Coordinate(lng, lat))
                driver.currentLocation = location
                driverProfileRepository.save(driver)
            }
        }
    }
}
