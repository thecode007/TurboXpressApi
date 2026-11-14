package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateRestaurantRequest
import com.thecode007.turboxpress.dto.RestaurantResponse
import com.thecode007.turboxpress.dto.UpdateRestaurantRequest
import com.thecode007.turboxpress.entity.Restaurant
import com.thecode007.turboxpress.repository.OwnerRepository
import com.thecode007.turboxpress.repository.RestaurantRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestaurantService(
    private val restaurantRepository: RestaurantRepository,
    private val ownerRepository: OwnerRepository,
    private val territoryRepository: com.thecode007.turboxpress.repository.TerritoryRepository
) {
    private val geometryFactory = GeometryFactory(org.locationtech.jts.geom.PrecisionModel(), 4326)

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    @Cacheable(value = ["restaurants"])
    fun getAllRestaurants(): List<RestaurantResponse> {
        return restaurantRepository.findAll().map { RestaurantResponse.from(it) }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    fun getRestaurantById(id: Long): RestaurantResponse {
        val restaurant = restaurantRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Restaurant not found with id: $id") }
        return RestaurantResponse.from(restaurant)
    }

    companion object {
        fun getCandidatePhoneNumbers(phoneNumber: String): List<String> {
            val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
            val candidates = mutableSetOf(clean)
            if (clean.startsWith("+20")) {
                candidates.add("0" + clean.removePrefix("+20"))
                candidates.add(clean.removePrefix("+20"))
                candidates.add(clean.removePrefix("+"))
            } else if (clean.startsWith("20")) {
                candidates.add("0" + clean.removePrefix("20"))
                candidates.add(clean.removePrefix("20"))
                candidates.add("+$clean")
            } else if (clean.startsWith("0")) {
                candidates.add("+20" + clean.removePrefix("0"))
                candidates.add("20" + clean.removePrefix("0"))
                candidates.add(clean.removePrefix("0"))
            } else {
                candidates.add("+20$clean")
                candidates.add("0$clean")
                candidates.add("20$clean")
            }
            return candidates.toList()
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    fun findRestaurantByOwnerPhone(phoneNumber: String): java.util.Optional<Restaurant> {
        return restaurantRepository.findFirstByOwnerPhoneNumberIn(getCandidatePhoneNumbers(phoneNumber))
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    fun getRestaurantByOwner(phoneNumber: String): RestaurantResponse {
        val restaurant = findRestaurantByOwnerPhone(phoneNumber)
            .orElseThrow { IllegalArgumentException("Restaurant not found for owner: $phoneNumber") }
        return RestaurantResponse.from(restaurant)
    }

    @Transactional
    @CacheEvict(value = ["restaurants"], allEntries = true)
    fun createRestaurant(request: CreateRestaurantRequest): RestaurantResponse {
        if (restaurantRepository.existsByName(request.name)) {
            throw IllegalArgumentException("A restaurant with name '${request.name}' already exists")
        }

        val owner = ownerRepository.findById(request.ownerPhoneNumber)
            .orElseThrow { IllegalArgumentException("Owner not found with phone number: ${request.ownerPhoneNumber}") }

        val location = geometryFactory.createPoint(Coordinate(request.longitude, request.latitude))
        val territory = territoryRepository.findActiveTerritoriesContainingPoint(location).firstOrNull()
        
        val restaurant = Restaurant(
            name = request.name,
            logoUrl = request.logoUrl,
            location = location,
            owner = owner,
            territory = territory,
            monthlySubFee = request.monthlySubFee,
            commissionRate = request.commissionRate
        )

        return RestaurantResponse.from(restaurantRepository.save(restaurant))
    }

    @Transactional
    @CacheEvict(value = ["restaurants"], allEntries = true)
    fun updateRestaurant(id: Long, request: UpdateRestaurantRequest): RestaurantResponse {
        val restaurant = restaurantRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Restaurant not found with id: $id") }

        if (request.name != null && restaurantRepository.existsByNameAndIdNot(request.name, id)) {
            throw IllegalArgumentException("A restaurant with name '${request.name}' already exists")
        }

        request.name?.let { restaurant.name = it }
        request.logoUrl?.let { restaurant.logoUrl = it }
        
        if (request.latitude != null && request.longitude != null) {
            val newLocation = geometryFactory.createPoint(Coordinate(request.longitude, request.latitude))
            restaurant.location = newLocation
            restaurant.territory = territoryRepository.findActiveTerritoriesContainingPoint(newLocation).firstOrNull()
        } else if (restaurant.territory == null) {
            restaurant.territory = territoryRepository.findActiveTerritoriesContainingPoint(restaurant.location).firstOrNull()
        }

        request.ownerPhoneNumber?.let {
            val owner = ownerRepository.findById(it)
                .orElseThrow { IllegalArgumentException("Owner not found with phone number: $it") }
            restaurant.owner = owner
        }

        request.monthlySubFee?.let { restaurant.monthlySubFee = it }
        request.commissionRate?.let { restaurant.commissionRate = it }

        return RestaurantResponse.from(restaurantRepository.save(restaurant))
    }

    @Transactional
    @CacheEvict(value = ["restaurants"], allEntries = true)
    fun deleteRestaurant(id: Long) {
        if (!restaurantRepository.existsById(id)) {
            throw IllegalArgumentException("Restaurant not found with id: $id")
        }
        restaurantRepository.deleteById(id)
    }
}
