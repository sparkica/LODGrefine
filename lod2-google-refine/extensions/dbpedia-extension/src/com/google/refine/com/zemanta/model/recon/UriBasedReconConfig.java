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

package com.google.refine.com.zemanta.model.recon;

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

import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Recon;
import com.google.refine.model.Recon.Judgment;
import com.google.refine.model.ReconCandidate;
import com.google.refine.model.Row;
import com.google.refine.model.recon.ReconConfig;
import com.google.refine.model.recon.ReconJob;
import com.google.refine.util.ParsingUtilities;

public class UriBasedReconConfig extends ZemantaStrictReconConfig {
    static public ReconConfig reconstruct(JSONObject obj) throws Exception {
        return new UriBasedReconConfig();
    }
    
    public UriBasedReconConfig() {
    }

    static protected class UriBasedReconJob extends ReconJob {
        String id;
        
        @Override
        public int getKey() {
            return id.hashCode();
        }
    }

    @Override
    public ReconJob createJob(Project project, int rowIndex, Row row,
            String columnName, Cell cell) {
            UriBasedReconJob job = new UriBasedReconJob();
            String s = cell.value.toString();
            
            if (!s.startsWith("/")) {
                    s = "/en/" + s;
            } else {
                    s = "/" + s;
            }
            
            job.id = s;
            
            return job;
    }

    @Override
    public int getBatchSize() {
        return 10;
    }

    @Override
    public String getBriefDescription(Project project, String columnName) {
        return "Reconcile cells in column " + columnName + " as URIs";
    }

    @Override
    public void write(JSONWriter writer, Properties options)
            throws JSONException {
        
        writer.object();
        writer.key("mode"); writer.value("strict");
        writer.key("match"); writer.value("id"); 
        writer.endObject();
    }
    
    @Override
    public List<Recon> batchRecon(List<ReconJob> jobs, long historyEntryID) {
        List<Recon> recons = new ArrayList<Recon>(jobs.size());
        Map<String, Recon> idToRecon = new HashMap<String, Recon>();
        
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
                        jsonWriter.key("type"); jsonWriter.array(); jsonWriter.endArray();
                        
                        jsonWriter.key("id|=");
                            jsonWriter.array();
                            for (ReconJob job : jobs) {
                                jsonWriter.value(((UriBasedReconJob) job).id);
                            }
                            jsonWriter.endArray();
                        
                    jsonWriter.endObject();
                    jsonWriter.endArray();
                jsonWriter.endObject();
                
                query = stringWriter.toString();
            }
            
            //TODO: change this to match Zemanta reconciliation service... eventually
            StringBuffer sb = new StringBuffer(1024);
            sb.append(zemapiRESTService);
            sb.append("?query=");
            sb.append(ParsingUtilities.encode(query));
            sb.append("&format=json");
            
            URL url = new URL(sb.toString());
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.connect();
            
            InputStream is = connection.getInputStream();
            try {
                String s = ParsingUtilities.inputStreamToString(is);
                JSONObject o = ParsingUtilities.evaluateJsonStringToObject(s);
                if (o.has("results")) {
                    JSONArray results = o.getJSONArray("bindings");
                    int count = results.length();

                    for (int i = 0; i < count; i++) {
                        JSONObject result = results.getJSONObject(i);

                        String id = result.getString("result");

                        JSONArray types = result.getJSONArray("type");
                        String[] typeIDs = new String[types.length()];
                        for (int j = 0; j < typeIDs.length; j++) {
                            typeIDs[j] = types.getString(j);
                        }

                        ReconCandidate candidate = new ReconCandidate(
                                id,
                                result.getString("name"),
                                typeIDs,
                                100
                        );

                        Recon recon = new ZemantaDataExtensionReconConfig().createNewRecon(historyEntryID);
                        recon.addCandidate(candidate);
                        recon.service = "zemanta";
                        recon.judgment = Judgment.Matched;
                        recon.judgmentAction = "auto";
                        recon.match = candidate;
                        recon.matchRank = 0;

                        idToRecon.put(id, recon);
                    }
                }
            } finally {
                is.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        for (ReconJob job : jobs) {
            String id = ((UriBasedReconJob) job).id;
            Recon recon = idToRecon.get(id);
            if (recon == null) {
                recon = createNoMatchRecon(historyEntryID);
            }
            recons.add(recon);
        }
        
        return recons;
    }

}
