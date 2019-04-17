package com.github.sisyphsu.common.cluster;

import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * SpringBootTest's base class.
 *
 * @author sulin
 * @since 2017-12-18 12:23:34
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ComponentScan
@SpringBootTest(classes = SpringBaseTest.Application.class)
public class SpringBaseTest extends AbstractJUnit4SpringContextTests {

    @SpringBootApplication
    public static class Application {

    }

}
