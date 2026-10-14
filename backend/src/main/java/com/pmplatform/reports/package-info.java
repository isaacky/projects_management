/**
 * Reports: read-only views across the other contexts, shown on screen and exported to Excel.
 *
 * <p>Owns no tables. Each report is a {@link com.pmplatform.reports.Report} bean that reads through
 * the other contexts' services, so figures match what the rest of the platform shows and the same
 * sign-in applies. Nothing depends on this context.
 */
package com.pmplatform.reports;
