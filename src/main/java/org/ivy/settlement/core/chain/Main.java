package org.ivy.settlement.core.chain;

import com.typesafe.config.ConfigRenderOptions;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.initializer.ComponentInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 类Main.java的实现描述：
 *
 * @Author laiyiyu
 */
public class Main {

    public static void main(String[] args) {
        var irisCoreSystemConfig = new IrisCoreSystemConfig();
        Logger logger = LoggerFactory.getLogger("main");
        logger.info("Config trace: " + irisCoreSystemConfig.getConfig().root().render(ConfigRenderOptions.defaults().setComments(false).setJson(false)));
        ComponentInitializer.init(irisCoreSystemConfig);
        logger.info("Main start success!!!:");
    }
}
