package com.google.refine.com.zemanta.tests.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;

import com.google.refine.com.zemanta.tests.DBpediaExtensionTest;
import com.google.refine.com.zemanta.util.DBpediaDataExtensionJob.DataExtension;
import com.google.refine.model.ReconCandidate;

public class DBpediaDataExtensionJobTest extends DBpediaExtensionTest {
    
    DBpediaDataExtensionJobStub extjob;
    String id = "test";
    JSONArray results = new JSONArray();
    Map<String, ReconCandidate> reconCandidateMap = null;
    JSONObject init = new JSONObject();
   
    @BeforeTest
    public void init() {
        logger = LoggerFactory.getLogger(this.getClass());
        
        try {
            init.append("key", "value");
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @BeforeMethod
    public void SetUp(){
        try {
            extjob = new DBpediaDataExtensionJobStub(init);
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @AfterMethod
    public void TearDown(){
        extjob = null;
    }


  @Test
  public void collectResultsPerRecord() {
      try {
        DataExtension result = extjob.collectResultsPerRecord(id, results, reconCandidateMap);
        Assert.assertNotNull(result);
        
      } catch (UnsupportedEncodingException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    } catch (JSONException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }
  }

  @Test
  public void countMaxRows() {
    throw new RuntimeException("Test not implemented");
  }

  @Test
  public void extractRecordsFromJSON() {
    throw new RuntimeException("Test not implemented");
  }

  @Test
  public void formulateSubqueryObj() {
    throw new RuntimeException("Test not implemented");
  }

  @Test
  public void formulateSubqueryProperties() {
    throw new RuntimeException("Test not implemented");
  }
}
