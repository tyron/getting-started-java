/* Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.getstarted.basicactions;

import com.example.getstarted.daos.BookDao;
import com.example.getstarted.daos.CloudSqlDao;
import com.example.getstarted.daos.DatastoreDao;
import com.example.getstarted.objects.Book;
import com.example.getstarted.objects.Result;
import com.example.getstarted.util.CloudStorageHelper;

import com.google.common.base.Strings;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opencensus.common.Scope;
import io.opencensus.exporter.trace.zipkin.ZipkinExporterConfiguration;
import io.opencensus.exporter.trace.zipkin.ZipkinTraceExporter;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Status;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.config.TraceParams;
import io.opencensus.trace.samplers.Samplers;


// [START example]
// a url pattern of "" makes this servlet the root servlet
@WebServlet(name = "list", urlPatterns = {"", "/books"}, loadOnStartup = 1)
@SuppressWarnings("serial")
public class ListBookServlet extends HttpServlet {

  private static final Logger logger = Logger.getLogger(ListBookServlet.class.getName());
  private static final Tracer tracer = Tracing.getTracer();
  
  @Override
  public void init() throws ServletException {
	
	// 1. Configure exporter to export traces to Zipkin.
	  ZipkinTraceExporter.createAndRegister(ZipkinExporterConfiguration
  												.builder()
  												.setV2Url("http://localhost:9411/api/v2/spans")
  												.setServiceName("tracing-to-zipkin")
  												.build());
	
	// 2. Configure 100% sample rate, otherwise, few traces will be sampled.
	TraceConfig traceConfig = Tracing.getTraceConfig();
	TraceParams activeTraceParams = traceConfig.getActiveTraceParams();
	traceConfig.updateActiveTraceParams(activeTraceParams.toBuilder().setSampler(Samplers.alwaysSample()).build());

	// 3. Get the global singleton Tracer object.
	// Tracer tracer = Tracing.getTracer();

	// 4. Create a scoped span, a scoped span will automatically end when closed.
	// It implements AutoClosable, so it'll be closed when the try block ends.
	/*try (Scope scope = tracer.spanBuilder("main").startScopedSpan()) {
		System.out.println("About to do some busy work...");
		for (int i = 0; i < 10; i++) {
			doWork(i);
		}
	}*/

	// 5. Gracefully shutdown the exporter, so that it'll flush queued traces to Zipkin.
	//Tracing.getExportComponent().shutdown();
	  
	BookDao dao = null;
    CloudStorageHelper storageHelper = new CloudStorageHelper();

    // Creates the DAO based on the Context Parameters
    String storageType = this.getServletContext().getInitParameter("bookshelf.storageType");
    switch (storageType) {
      case "datastore":
        dao = new DatastoreDao();
        break;
      case "cloudsql":
        try {
          String connect = this.getServletContext().getInitParameter("sql.urlRemote");
          if (connect.contains("localhost")) {
            connect = this.getServletContext().getInitParameter("sql.urlLocal");
          }
          dao = new CloudSqlDao(connect);
        } catch (SQLException e) {
          throw new ServletException("SQL error", e);
        }
        break;
      default:
        throw new IllegalStateException(
            "Invalid storage type. Check if bookshelf.storageType property is set.");
    }
    this.getServletContext().setAttribute("dao", dao);
    this.getServletContext().setAttribute("storageHelper", storageHelper);
    this.getServletContext().setAttribute(
        "isCloudStorageConfigured",    // Hide upload when Cloud Storage is not configured.
        !Strings.isNullOrEmpty(getServletContext().getInitParameter("bookshelf.bucket")));
    // [START authConfigured]
    this.getServletContext().setAttribute(
        "isAuthConfigured",            // Hide login when auth is not configured.
        !Strings.isNullOrEmpty(getServletContext().getInitParameter("bookshelf.clientID")));
    // [END authConfigured]
  }

@Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException,
      ServletException {
  // ZipkinTraceExporter.createAndRegister("http://localhost:9411/api/v2/spans", "tracing-to-zipkin-service");
	try (Scope scope = tracer.spanBuilder("books.list").startScopedSpan()) {
		BookDao dao = (BookDao) this.getServletContext().getAttribute("dao");
	    String startCursor = req.getParameter("cursor");
	    List<Book> books = null;
	    String endCursor = null;
	    
	    Span span = tracer.getCurrentSpan();
	    try {
	      Result<Book> result = dao.listBooks(startCursor);
	      logger.log(Level.INFO, "Retrieved list of all books!");
	      books = result.result;
	      endCursor = result.cursor;
	    } catch (Exception e) {
	      span.setStatus(Status.INTERNAL.withDescription(e.toString()));
	      throw new ServletException("Error listing books", e);
	    }
	    req.getSession().getServletContext().setAttribute("books", books);
	    StringBuilder bookNames = new StringBuilder();
	    for (Book book : books) {
	      bookNames.append(book.getTitle() + " ");
	    }
	    logger.log(Level.INFO, "Loaded books: " + bookNames.toString());
	    req.setAttribute("cursor", endCursor);
	    req.setAttribute("page", "list");

	    // 7. Annotate our span to capture metadata about our operation
	    Map<String, AttributeValue> attributes = new HashMap<String, AttributeValue>();
	    attributes.put("books", AttributeValue.longAttributeValue(books.size()));
	    span.addAnnotation("Invoking doGet", attributes);
	    
	    req.getRequestDispatcher("/base.jsp").forward(req, resp);
	}
  }
}
// [END example]
