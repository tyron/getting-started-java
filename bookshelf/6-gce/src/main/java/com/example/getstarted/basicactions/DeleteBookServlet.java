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

import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
import io.opencensus.trace.Status;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// [START example]
@SuppressWarnings("serial")
@WebServlet(name = "delete", value = "/delete")
public class DeleteBookServlet extends HttpServlet {
	
	private static final Tracer tracer = Tracing.getTracer();
	
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try (Scope scope = tracer.spanBuilder("books.delete").startScopedSpan()) {
			Span span = tracer.getCurrentSpan();
			Long id = Long.decode(req.getParameter("id"));
			BookDao dao = (BookDao) this.getServletContext().getAttribute("dao");
			try {
				dao.deleteBook(id);
				resp.sendRedirect("/books");
			} catch (Exception e) {
				span.setStatus(Status.INTERNAL.withDescription(e.toString()));
				throw new ServletException("Error deleting book", e);
			}
		}
	}
}
// [END example]
