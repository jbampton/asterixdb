/*
 * Copyright 2009-2013 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.asterix.api.http.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;

public class FeedDataProviderServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private Random random = new Random();

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String feedName = request.getParameter("feed");
        String datasetName = request.getParameter("dataset");
        String dataverseName = request.getParameter("dataverse");

        JSONObject obj = new JSONObject();
        try {
            obj.put("time", System.currentTimeMillis());
            obj.put("value", random.nextInt(100) + "");
        } catch (JSONException jsoe) {
            throw new IOException(jsoe);
        }

        PrintWriter out = response.getWriter();
        out.println(obj.toString());
    }

}