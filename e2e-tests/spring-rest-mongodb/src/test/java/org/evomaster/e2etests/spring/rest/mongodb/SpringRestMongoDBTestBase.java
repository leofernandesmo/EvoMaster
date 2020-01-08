package org.evomaster.e2etests.spring.rest.mongodb;

import org.evomaster.client.java.controller.EmbeddedSutController;
import org.evomaster.e2etests.utils.RestTestBase;

public class SpringRestMongoDBTestBase extends RestTestBase {


    protected static void initClass(EmbeddedSutController controller) throws Exception {
        RestTestBase.initClass(controller);
    }
}
