/*
 * Copyright (c) 2010,2011 Thomas F. Morris
 *        All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice, this 
 *   list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, 
 *   this list of conditions and the following disclaimer in the documentation 
 *   and/or other materials provided with the distribution.
 * 
 * Neither the name of Google nor the names of its contributors may be used to 
 * endorse or promote products derived from this software without specific 
 * prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR 
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.refine.extension.gdata;

import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gdata.client.GoogleService;
import com.google.gdata.client.Service.GDataRequest;
import com.google.gdata.client.Service.GDataRequest.RequestType;
import com.google.gdata.client.docs.DocsService;
import com.google.gdata.client.spreadsheet.CellQuery;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.Link;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.batch.BatchOperationType;
import com.google.gdata.data.batch.BatchStatus;
import com.google.gdata.data.batch.BatchUtils;
import com.google.gdata.data.docs.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.Cell;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;

import com.google.refine.ProjectManager;
import com.google.refine.browsing.Engine;
import com.google.refine.commands.Command;
import com.google.refine.commands.HttpUtilities;
import com.google.refine.commands.project.ExportRowsCommand;
import com.google.refine.exporters.CustomizableTabularExporterUtilities;
import com.google.refine.exporters.TabularSerializer;
import com.google.refine.model.Project;

public class UploadCommand extends Command {
    static final Logger logger = LoggerFactory.getLogger("gdata_upload");
    
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String token = TokenCookie.getToken(request);
        if (token == null) {
            HttpUtilities.respond(response, "error", "Not authorized");
            return;
        }

        ProjectManager.singleton.setBusy(true);
        try {
            Project project = getProject(request);
            Engine engine = getEngine(request, project);
            Properties params = ExportRowsCommand.getRequestParameters(request);
            String name = params.getProperty("name");
            
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Content-Type", "application/json");
            
            Writer w = response.getWriter();
            JSONWriter writer = new JSONWriter(w);
            try {
                writer.object();
                
                List<Exception> exceptions = new LinkedList<Exception>();
                String url = upload(project, engine, params, token, name, exceptions);
                if (url != null) {
                    writer.key("status"); writer.value("ok");
                    writer.key("url"); writer.value(url);
                } else if (exceptions.size() == 0) {
                    writer.key("status"); writer.value("error");
                    writer.key("message"); writer.value("No such format");
                } else {
                    for (Exception e : exceptions) {
                        logger.warn(e.getLocalizedMessage(), e);
                    }
                    writer.key("status"); writer.value("error");
                    writer.key("message"); writer.value(exceptions.get(0).getLocalizedMessage());
                }
            } catch (Exception e) {
                e.printStackTrace();
                writer.key("status"); writer.value("error");
                writer.key("message"); writer.value(e.getMessage());
            } finally {
                writer.endObject();
                w.flush();
                w.close();
            }
        } catch (Exception e) {
            throw new ServletException(e);
        } finally {
            ProjectManager.singleton.setBusy(false);
        }
    }
    
    static private String upload(
            Project project, Engine engine, Properties params,
            String token, String name, List<Exception> exceptions) {
        String format = params.getProperty("format");
        if ("gdata/google-spreadsheet".equals(format)) {
            return uploadSpreadsheet(project, engine, params, token, name, exceptions);
        } else if ("gdata/fusion-table".equals(format)) {
            return uploadFusionTable(project, engine, params, token, name, exceptions);
        }
        return null;
    }
    
    static private String uploadSpreadsheet(
            final Project project, final Engine engine, final Properties params,
            String token, String name, List<Exception> exceptions) {
        
        DocsService docsService = GDataExtension.getDocsService(token);
        final SpreadsheetService spreadsheetService = GDataExtension.getSpreadsheetService(token);
        
        try {
            SpreadsheetEntry spreadsheetEntry = new SpreadsheetEntry();
            spreadsheetEntry.setTitle(new PlainTextConstruct(name));
            
            final SpreadsheetEntry spreadsheetEntry2 = docsService.insert(
                new URL("https://docs.google.com/feeds/default/private/full/"), spreadsheetEntry);
            
            int[] size = CustomizableTabularExporterUtilities.countColumnsRows(
                    project, engine, params);
            
            URL worksheetFeedUrl = spreadsheetEntry2.getWorksheetFeedUrl();
            WorksheetEntry worksheetEntry = new WorksheetEntry(size[1], size[0]); 
            worksheetEntry.setTitle(new PlainTextConstruct("Uploaded Data"));
            
            final WorksheetEntry worksheetEntry2 =
                spreadsheetService.insert(worksheetFeedUrl, worksheetEntry);
            
            spreadsheetEntry2.getDefaultWorksheet().delete();
            
            new Thread() {
                @Override
                public void run() {
                    spreadsheetService.setProtocolVersion(SpreadsheetService.Versions.V1);
                    try {
                        uploadToCellFeed(
                            project, engine, params,
                            spreadsheetService,
                            spreadsheetEntry2,
                            worksheetEntry2);
                    } catch (Exception e) {
                        logger.error("Error uploading data to Google Spreadsheets", e);
                    }
                }
            }.start();
            
            return spreadsheetEntry2.getDocumentLink().getHref();
        } catch (MalformedURLException e) {
            exceptions.add(e);
        } catch (IOException e) {
            exceptions.add(e);
        } catch (ServiceException e) {
            exceptions.add(e);
        }
        return null;
    }
    
    static private void uploadToCellFeed(
            Project project,
            Engine engine,
            Properties params,
            final SpreadsheetService service,
            final SpreadsheetEntry spreadsheetEntry,
            final WorksheetEntry worksheetEntry)
            throws IOException, ServiceException {
        
        final URL cellFeedUrl = worksheetEntry.getCellFeedUrl();
        final CellEntry[][] cellEntries =
            new CellEntry[worksheetEntry.getRowCount()][worksheetEntry.getColCount()];
        {
            CellQuery cellQuery = new CellQuery(cellFeedUrl);
            cellQuery.setReturnEmpty(true);
            
            CellFeed fetchingCellFeed = service.getFeed(cellQuery, CellFeed.class);
            for (CellEntry cellEntry : fetchingCellFeed.getEntries()) {
              Cell cell = cellEntry.getCell();
              cellEntries[cell.getRow() - 1][cell.getCol() - 1] = cellEntry;
            }
        }
        
        TabularSerializer serializer = new TabularSerializer() {
            CellFeed cellFeed = service.getFeed(cellFeedUrl, CellFeed.class);
            CellFeed batchRequest = null;
            int row = 0;
            
            @Override
            public void startFile(JSONObject options) {
            }
            
            @Override
            public void endFile() {
                if (batchRequest != null) {
                    sendBatch();
                }
            }
            
            private void sendBatch() {
                try {
                    Link batchLink = cellFeed.getLink(Link.Rel.FEED_BATCH, Link.Type.ATOM);
                    CellFeed batchResponse = service.batch(new URL(batchLink.getHref()), batchRequest);
                    
                    for (CellEntry entry : batchResponse.getEntries()) {
                      String batchId = BatchUtils.getBatchId(entry);
                      if (!BatchUtils.isSuccess(entry)) {
                        BatchStatus status = BatchUtils.getBatchStatus(entry);
                        logger.warn(
                            String.format(
                                "Error: %s failed (%s) %s\n",
                                batchId, status.getReason(), status.getContent()));
                        break;
                      }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                batchRequest = null;
            }
            
            @Override
            public void addRow(List<CellData> cells, boolean isHeader) {
                if (batchRequest == null) {
                    batchRequest = new CellFeed();
                }
                for (int c = 0; c < cells.size(); c++) {
                    CellData cellData = cells.get(c);
                    if (cellData != null && cellData.text != null) {
                        String cellId = String.format("R%sC%s", row + 1, c + 1);
                        
                        CellEntry cellEntry = cellEntries[row][c];
                        cellEntry.changeInputValueLocal(cellData.text);
                        if (cellData.link != null) {
                            cellEntry.addHtmlLink(cellData.link, null, cellData.text);
                        }
                        cellEntry.setId(cellId);
                        BatchUtils.setBatchId(cellEntry, cellId);
                        BatchUtils.setBatchOperationType(cellEntry, BatchOperationType.UPDATE);
                        
                        batchRequest.getEntries().add(cellEntry);
                    }
                }
                row++;
                if (row % 20 == 0) {
                    sendBatch();
                }
            }
        };
        
        CustomizableTabularExporterUtilities.exportRows(
                project, engine, params, serializer);
    }
    
    static private String uploadFusionTable(
            Project project, final Engine engine, final Properties params,
            String token, String name, List<Exception> exceptions) {
        GoogleService service = GDataExtension.getFusionTablesGoogleService(token);
        FusionTableSerializer serializer = new FusionTableSerializer(service, name, exceptions);
        
        CustomizableTabularExporterUtilities.exportRows(
                project, engine, params, serializer);
        
        return serializer.tableId == null || exceptions.size() > 0 ? null :
            "https://www.google.com/fusiontables/DataSource?dsrcid=" + serializer.tableId;
    }
    
    final static private class FusionTableSerializer implements TabularSerializer {
        GoogleService service;
        String tableName;
        List<Exception> exceptions;
        
        String tableId;
        List<String> columnNames;
        StringBuffer sbBatch;
        int rows;
        
        FusionTableSerializer(GoogleService service, String tableName, List<Exception> exceptions) {
            this.service = service;
            this.tableName = tableName;
            this.exceptions = exceptions;
        }
        
        @Override
        public void startFile(JSONObject options) {
        }

        @Override
        public void endFile() {
            if (sbBatch != null) {
                sendBatch();
            }
        }

        @Override
        public void addRow(List<CellData> cells, boolean isHeader) {
            if (isHeader) {
                columnNames = new ArrayList<String>(cells.size());
                
                StringBuffer sb = new StringBuffer();
                sb.append("CREATE TABLE '");
                sb.append(tableName);
                sb.append("' (");
                boolean first = true;
                for (CellData cellData : cells) {
                    columnNames.add(cellData.text);
                    
                    if (first) {
                        first = false;
                    } else {
                        sb.append(',');
                    }
                    sb.append("'");
                    sb.append(cellData.text);
                    sb.append("': STRING");
                }
                sb.append(")");
                
                try {
                    String createQuery = sb.toString();
                    
                    GDataRequest createTableRequest = GDataExtension.createFusionTablesPostRequest(
                            service, RequestType.INSERT, createQuery);
                    createTableRequest.execute();
                    
                    List<List<String>> createTableResults =
                        GDataExtension.parseFusionTablesResults(createTableRequest);
                    if (createTableResults != null && createTableResults.size() == 2) {
                        tableId = createTableResults.get(1).get(0);
                    }
                } catch (Exception e) {
                    exceptions.add(e);
                }
            } else if (tableId != null) {
                if (sbBatch == null) {
                    sbBatch = new StringBuffer();
                }
                formulateInsert(cells, sbBatch);
                
                rows++;
                if (rows % 20 == 0) {
                    sendBatch();
                }
            }
        }
        
        void sendBatch() {
            try {
                GDataRequest createTableRequest = GDataExtension.createFusionTablesPostRequest(
                        service, RequestType.INSERT, sbBatch.toString());
                createTableRequest.execute();
            } catch (IOException e) {
                exceptions.add(e);
            } catch (ServiceException e) {
                exceptions.add(e);
            } finally {
                sbBatch = null;
            }
        }
        
        void formulateInsert(List<CellData> cells, StringBuffer sb) {
            StringBuffer sbColumnNames = new StringBuffer();
            StringBuffer sbValues = new StringBuffer();
            boolean first = true;
            for (int i = 0; i < cells.size() && i < columnNames.size(); i++) {
                CellData cellData = cells.get(i);
                if (first) {
                    first = false;
                } else {
                    sbColumnNames.append(',');
                    sbValues.append(',');
                }
                sbColumnNames.append("'");
                sbColumnNames.append(columnNames.get(i));
                sbColumnNames.append("'");
                
                sbValues.append("'");
                if (cellData != null && cellData.text != null) {
                    sbValues.append(cellData.text.replaceAll("'", "\\\\'"));
                }
                sbValues.append("'");
            }
            
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append("INSERT INTO ");
            sb.append(tableId);
            sb.append("(");
            sb.append(sbColumnNames.toString());
            sb.append(") values (");
            sb.append(sbValues.toString());
            sb.append(")");
        }
    }
}
