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

package com.google.refine.com.zemanta.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import com.google.refine.commands.Command;
import com.google.refine.com.zemanta.util.DBpediaDataExtensionJob;
import com.google.refine.com.zemanta.util.DBpediaDataExtensionJob.ColumnInfo;
import com.google.refine.com.zemanta.util.DBpediaDataExtensionJob.DataExtension;
import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.ReconCandidate;
import com.google.refine.model.Row;
import com.google.refine.util.ParsingUtilities;

public class DBpediaPreviewExtendDataCommand extends Command {
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        try {
            Project project = getProject(request);
            String columnName = request.getParameter("columnName");
            
            String rowIndicesString = request.getParameter("rowIndices");
            if (rowIndicesString == null) {
                respond(response, "{ \"code\" : \"error\", \"message\" : \"No row indices specified\" }");
                return;
            }
            
            String jsonString = request.getParameter("extension");
            JSONObject json = ParsingUtilities.evaluateJsonStringToObject(jsonString);
            
            JSONArray rowIndices = ParsingUtilities.evaluateJsonStringToArray(rowIndicesString);
            int cellIndex = project.columnModel.getColumnByName(columnName).getCellIndex();
            
            List<String> topicNames = new ArrayList<String>();
            List<String> topicIds = new ArrayList<String>();
            Set<String> ids = new HashSet<String>();
            extractTopicsAndIDs(project, rowIndices, cellIndex, topicNames, topicIds, ids);
            
            Map<String, ReconCandidate> reconCandidateMap = new HashMap<String, ReconCandidate>();
            DBpediaDataExtensionJob job = new DBpediaDataExtensionJob(json);
            Map<String, DataExtension> map = job.extend(ids, reconCandidateMap);

            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Type", "application/json");
            constructResponseJSON(response, topicNames, topicIds, job, map);
            
        } catch (Exception e) {
            respondException(response, e);
        }
    }

    protected void extractTopicsAndIDs(Project project, JSONArray rowIndices, int cellIndex,
            List<String> topicNames, List<String> topicIds, Set<String> ids)
            throws JSONException {
        
        int length = rowIndices.length();
        for (int i = 0; i < length; i++) {
            int rowIndex = rowIndices.getInt(i);
            if (rowIndex >= 0 && rowIndex < project.rows.size()) {
                Row row = project.rows.get(rowIndex);
                Cell cell = row.getCell(cellIndex);
                if (cell != null && cell.recon != null && cell.recon.match != null) {
                    topicNames.add(cell.recon.match.name);
                    topicIds.add(cell.recon.match.id);
                    ids.add(cell.recon.match.id);
                } else {
                    topicNames.add(null);
                    topicIds.add(null);
                    ids.add(null);
                }
            }
        }
    }

    protected void constructResponseJSON(HttpServletResponse response, List<String> topicNames, List<String> topicIds,
            DBpediaDataExtensionJob job, Map<String, DataExtension> map)
            throws IOException, JSONException {
        JSONWriter writer = new JSONWriter(response.getWriter());
        writer.object();
        writer.key("code"); writer.value("ok");
        writer.key("columns");
            writer.array();
            for (ColumnInfo info : job.columns) {
                writer.object();
                writer.key("names");
                    writer.array();
                    for (String name : info.names) {
                        writer.value(name);
                    }
                    writer.endArray();
                writer.key("path");
                    writer.array();
                    for (String id : info.path) {
                        writer.value(id);
                    }
                    writer.endArray();
                writer.endObject();
            }
            writer.endArray();
        
        writer.key("rows");
            writer.array();
            for (int r = 0; r < topicNames.size(); r++) {
                String id = topicIds.get(r);
                String topicName = topicNames.get(r);
                
                if (id != null && map.containsKey(id)) {
                    DataExtension ext = map.get(id);
                    boolean first = true;
                    
                    if (ext.data.length > 0) {
                        for (Object[] row : ext.data) {
                            writer.array();
                            if (first) {
                                writer.value(topicName);
                                first = false;
                            } else {
                                writer.value(null);
                            }
                            
                            for (Object cell : row) {
                                if (cell != null && cell instanceof ReconCandidate) {
                                    ReconCandidate rc = (ReconCandidate) cell;
                                    writer.object();
                                    writer.key("id"); writer.value(rc.id);
                                    writer.key("name"); writer.value(rc.name);
                                    writer.endObject();
                                } else {
                                    writer.value(cell);
                                }
                            }
                            
                            writer.endArray();
                        }
                        continue;
                    }
                }
                
                writer.array();
                if (id != null) {
                    writer.object();
                    writer.key("id"); writer.value(id);
                    writer.key("name"); writer.value(topicName);
                    writer.endObject();
                } else {
                    writer.value("<not reconciled>");
                }
                writer.endArray();
            }
            writer.endArray();
            
        writer.endObject();
    }
}
