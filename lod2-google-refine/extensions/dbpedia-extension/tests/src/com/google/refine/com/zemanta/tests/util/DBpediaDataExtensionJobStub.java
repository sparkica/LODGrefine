package com.google.refine.com.zemanta.tests.util;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.refine.com.zemanta.util.DBpediaDataExtensionJob;
import com.google.refine.model.ReconCandidate;


public class DBpediaDataExtensionJobStub extends DBpediaDataExtensionJob {
    

    public DBpediaDataExtensionJobStub(JSONObject obj) throws JSONException {
        super(obj);
        // TODO Auto-generated constructor stub
    }
    
    public DBpediaDataExtensionJob.DataExtension collectResultsPerRecord(String id,
            JSONArray results, 
            Map<String, ReconCandidate> reconCandidateMap
            ) throws JSONException, UnsupportedEncodingException
            {
                return super.collectResultsPerRecord(id, results, reconCandidateMap);
            }

}
