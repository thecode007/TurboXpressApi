import com.thecode007.turboxpress.routing.PricingService
import com.graphhopper.GraphHopper
import org.springframework.boot.test.context.SpringBootTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@SpringBootTest
class PricingServiceTest {

    @Autowired
    lateinit var pricingService: PricingService

    @Test
    fun testPricing() {
        println("Testing pricing...")
    }
}
