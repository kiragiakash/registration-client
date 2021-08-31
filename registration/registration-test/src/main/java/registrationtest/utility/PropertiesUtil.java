
package registrationtest.utility;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class PropertiesUtil {
    private static final Logger logger = LogManager.getLogger(PropertiesUtil.class);

    public static String getKeyValue(String key) throws IOException {

        String configFilePath = System.getProperty("user.dir") + System.getProperty("path.config");

        FileReader reader = new FileReader(configFilePath);
        // create properties object
        Properties p = new Properties();

        // Add a wrapper around reader object
        p.load(reader);

        // access properties data
        return p.getProperty(key);

    }

    public static void main(String[] args) throws IOException {
        String value = getKeyValue("PropertyFilePath");
        System.out.println(value);
    }

}
