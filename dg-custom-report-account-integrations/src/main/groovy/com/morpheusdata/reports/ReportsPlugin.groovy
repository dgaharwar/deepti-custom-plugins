package com.morpheusdata.reports

import com.morpheusdata.core.Plugin

/**
 * Example Custom Reports Plugin - Account Integrations
 */
class ReportsPlugin extends Plugin {

	@Override
	String getCode() {
		return 'morpheus-reports-plugin-account-integrations'
	}

	@Override
	void initialize() {
		CustomReportProvider customReportProvider = new CustomReportProvider(this, morpheus)
		this.pluginProviders.put(customReportProvider.code, customReportProvider)
		this.setName("Custom Report - Account Integrations")
		
	}

	@Override
	void onDestroy() {
	}
}
