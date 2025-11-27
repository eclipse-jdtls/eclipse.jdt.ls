/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.javadoc;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class TableHelper {


	/**
	 * Adds a new row header if the given table doesn't have any.
	 *
	 * @param table
	 *                  the HTML table to check for a header
	 */
	public static void normalizeTableHeaders(Element table) {

		addMissingTableHeaders(table);

		Element thead = table.select("thead").first();
		if (thead != null) {
			Elements theadRows = thead.select("tr");

			// If thead has more than 1 row, move extra rows to tbody
			if (theadRows.size() > 1) {
				Element tbody = table.select("tbody").first();
				if (tbody == null) {
					tbody = new Element("tbody");
					table.appendChild(tbody);
				}
				// Move all rows except the first one from thead to the beginning of tbody
				for (int i = theadRows.size() - 1; i >= 1; i--) {
					Element row = theadRows.get(i);
					row.remove();
					tbody.prependChild(row);
				}
			}

			// Convert any remaining th cells in tbody to td with bold content
			table.select("tbody th").forEach(TableHelper::convertThToTd);
		}

		//Convert mixed th/td rows: replace th with bold td for proper Markdown rendering
		table.select("tr").forEach(TableHelper::normalizeMixedTableRow);
	}

	/**
	 * Adds a new header row if the given table doesn't have any.
	 *
	 * @param table
	 *                  the HTML table to check for a header
	 */
	private static void addMissingTableHeaders(Element table) {
		int numCols = 0;
		Elements theadElements = table.select("thead");
		if (!theadElements.isEmpty()) {
			return;
		}
		Element thead = new Element("thead");

		//Insert thead in 1st position in the table
		table.insertChildren(0, thead);
		Elements tbodyElements = table.select("tbody");
		if (!tbodyElements.isEmpty()) {
			Element tbody = tbodyElements.first();
			Elements tbodyRows = tbody.select("tr");
			if (!tbodyRows.isEmpty()) {
				// Move tbody's header row to thead
				Element potentialHeader = tbodyRows.first();
				int cols1stRow = potentialHeader.childrenSize();
				int thSize = potentialHeader.getElementsByTag("th").size();
				if (thSize == cols1stRow) { // first row contains <th> elements only, move it to the header
					thead.appendChild(potentialHeader);
					return;
				}

				// Find the largest number of columns in any row
				for (Element row : tbodyRows) {
					int colSize = row.getElementsByTag("td").size() + row.getElementsByTag("th").size();
					//Count the number of columns in the row, keeping the biggest count
					if (colSize > numCols) {
						numCols = colSize;
					}
				}
			}
		}
		if (numCols > 0 && thead.childrenSize() == 0) {
			//Create a new header row based on the number of columns already found
			Element newHeader = new Element("tr");
			for (int i = 0; i < numCols; i++) {
				newHeader.appendChild(new Element("th"));
			}
			// Add the new header row to the thead
			thead.appendChild(newHeader);
		}
	}

	/**
	 * Normalizes table rows that contain both th and td elements. Converts th
	 * elements to td with bold content for proper Markdown rendering.
	 *
	 * @param row
	 *                the table row to normalize
	 */
	private static void normalizeMixedTableRow(Element row) {
		Elements thElements = row.getElementsByTag("th");
		Elements tdElements = row.getElementsByTag("td");

		// Only process if row has both th and td elements
		if (!thElements.isEmpty() && !tdElements.isEmpty()) {
			thElements.forEach(TableHelper::convertThToTd);
		}
	}

	/**
	 * Converts a &lt;th&gt; element to a &lt;td&gt; element with bold content.
	 * Preserves all attributes and wraps the content in a strong tag for bold
	 * formatting.
	 *
	 * @param th
	 *               the &lt;th&gt; element to convert to a &lt;td&gt; element with
	 *               bold content
	 */
	private static void convertThToTd(Element th) {
		// Create a new td element with the same attributes
		Element td = new Element("td");
		td.attributes().addAll(th.attributes());

		// Wrap the content in <strong> to preserve bold formatting
		Element strong = new Element("strong");
		strong.appendChildren(th.childNodes());
		td.appendChild(strong);

		// Replace th with td
		th.replaceWith(td);
	}
}
