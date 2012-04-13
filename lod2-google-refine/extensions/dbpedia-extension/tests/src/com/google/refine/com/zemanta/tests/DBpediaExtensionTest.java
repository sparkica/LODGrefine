package com.google.refine.com.zemanta.tests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.slf4j.Logger;
import org.testng.annotations.BeforeSuite;


public class DBpediaExtensionTest {

    protected Logger logger;

    @BeforeSuite
    public void init() {
        System.setProperty("log4j.configuration", "tests.log4j.properties");
    }
    

}
