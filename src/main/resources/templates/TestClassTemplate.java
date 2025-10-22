package ${PACKAGE};

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import javax.annotation.Resource;

@RunWith(SpringRunner.class)
@ContextConfiguration(locations = "classpath:${RELATIVE_PATH}/${TEST_CLASS}.xml")
public class ${TEST_CLASS} {

@Resource
private ${CLASS} ${BEAN};

@Test
public void test${METHOD}() {
        // TODO: add assertions
        System.out.println("${BEAN} = " + ${BEAN});
    }
}
