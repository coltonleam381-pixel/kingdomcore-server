import org.bukkit.OfflinePlayer;
import java.lang.reflect.Method;

public class TestOffline {
    public static void main(String[] args) {
        for (Method m : OfflinePlayer.class.getMethods()) {
            System.out.println(m.getName());
        }
    }
}
