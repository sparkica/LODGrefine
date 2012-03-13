/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.google.refine.freebase.model.recon;

import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import com.google.refine.freebase.FreebaseTopic;
import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Recon;
import com.google.refine.model.Recon.Judgment;
import com.google.refine.model.ReconCandidate;
import com.google.refine.model.Row;
import com.google.refine.model.recon.ReconConfig;
import com.google.refine.model.recon.ReconJob;
import com.google.refine.util.ParsingUtilities;

public class KeyBasedReconConfig extends StrictReconConfig {
    final public FreebaseTopic namespace;
    
    static public ReconConfig reconstruct(JSONObject obj) throws Exception {
        JSONObject ns = obj.getJSONObject("namespace");
        
        return new KeyBasedReconConfig(
            new FreebaseTopic(
                ns.getString("id"),
                ns.getString("name")
            )
        );
    }
    
    public KeyBasedReconConfig(FreebaseTopic namespace) {
        this.namespace = namespace;
    }

    static protected class KeyBasedReconJob extends ReconJob {
        String key;
        
        @Override
        public int getKey() {
            return key.hashCode();
        }
    }

    @Override
    public ReconJob createJob(Project project, int rowIndex, Row row,
            String columnName, Cell cell) {
        
        KeyBasedReconJob job = new KeyBasedReconJob();
        
        job.key = cell.value.toString().replace(' ', '_');
        
        return job;
    }

    @Override
    public int getBatchSize() {
        return 10;
    }

    @Override
    public String getBriefDescription(Project project, String columnName) {
        return "Reconcile cells in column " + columnName + " to topics with keys in namespace " + namespace.id;
    }

    @Override
    public void write(JSONWriter writer, Properties options)
            throws JSONException {
        
        writer.object();
        writer.key("mode"); writer.value("strict");
        writer.key("match"); writer.value("key"); 
        writer.key("namespace"); namespace.write(writer, options); 
        writer.endObject();
    }
    
    @Override
    public List<Recon> batchRecon(List<ReconJob> jobs, long historyEntryID) {
        List<Recon> recons = new ArrayList<Recon>(jobs.size());
        Map<String, Recon> keyToRecon = new HashMap<String, Recon>();
        
        try {
            String query = null;
            {
                StringWriter stringWriter = new StringWriter();
                JSONWriter jsonWriter = new JSONWriter(stringWriter);
                
                jsonWriter.object();
                jsonWriter.key("query");
                    jsonWriter.array();
                    jsonWriter.object();
                    
                        jsonWriter.key("id"); jsonWriter.value(null);
                        jsonWriter.key("name"); jsonWriter.value(null);
                        jsonWriter.key("guid"); jsonWriter.value(null);
                        jsonWriter.key("type"); jsonWriter.array(); jsonWriter.endArray();
                        
                        jsonWriter.key("key");
                            jsonWriter.array();
                            jsonWriter.object();
                            
                            jsonWriter.key("namespace");
                                jsonWriter.object();
                                jsonWriter.key("id"); jsonWriter.value(namespace.id);
                                jsonWriter.endObject();
                                
                            jsonWriter.key("value"); jsonWriter.value(null);
                            jsonWriter.key("value|=");
                                jsonWriter.array();
                                for (ReconJob job : jobs) {
                                    jsonWriter.value(((KeyBasedReconJob) job).key);
                                }
                                jsonWriter.endArray();
                                
                            jsonWriter.endObject();
                            jsonWriter.endArray();
                        
                    jsonWriter.endObject();
                    jsonWriter.endArray();
                jsonWriter.endObject();
                
                query = stringWriter.toString();
            }
            
            StringBuffer sb = new StringBuffer(1024);
            sb.append(s_mqlreadService);
            sb.append("?query=");
            sb.append(ParsingUtilities.encode(query));
            
            URL url = new URL(sb.toString());
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.connect();
            
            InputStream is = connection.getInputStream();
            try {
                String s = ParsingUtilities.inputStreamToString(is);
                JSONObject o = ParsingUtilities.evaluateJsonStringToObject(s);
                if (o.has("result")) {
                    JSONArray results = o.getJSONArray("result");
                    int count = results.length();

                    for (int i = 0; i < count; i++) {
                        JSONObject result = results.getJSONObject(i);

                        String key = result.getJSONArray("key").getJSONObject(0).getString("value");

                        JSONArray types = result.getJSONArray("type");
                        String[] typeIDs = new String[types.length()];
                        for (int j = 0; j < typeIDs.length; j++) {
                            typeIDs[j] = types.getString(j);
                        }

                        ReconCandidate candidate = new ReconCandidate(
                                result.getString("id"),
                                result.getString("name"),
                                typeIDs,
                                100
                        );

                        Recon recon = Recon.makeFreebaseRecon(historyEntryID);
                        recon.addCandidate(candidate);
                        recon.service = "mql";
                        recon.judgment = Judgment.Matched;
                        recon.judgmentAction = "auto";
                        recon.match = candidate;
                        recon.matchRank = 0;

                        keyToRecon.put(key, recon);
                    }
                }
            } finally {
                is.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (ReconJob job : jobs) {
            String key = ((KeyBasedReconJob) job).key;
            Recon recon = keyToRecon.get(key);
            if (recon == null) {
                recon = createNoMatchRecon(historyEntryID);
            }
            recons.add(recon);
        }
        
        return recons;
    }

}
