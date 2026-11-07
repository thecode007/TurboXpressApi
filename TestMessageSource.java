import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.Locale;

public class TestMessageSource {
    public static void main(String[] args) {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        try {
            System.out.println(source.getMessage("notification.DRIVER_ASSIGNED.body", new Object[]{"123"}, Locale.ENGLISH));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
