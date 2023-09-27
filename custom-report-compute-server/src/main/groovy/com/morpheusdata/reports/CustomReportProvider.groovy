package com.morpheusdata.reports

import com.morpheusdata.core.AbstractReportProvider
import com.morpheusdata.core.MorpheusContext
import com.morpheusdata.core.Plugin
import com.morpheusdata.model.OptionType
import com.morpheusdata.model.ReportResult
import com.morpheusdata.model.ReportType
import com.morpheusdata.model.ReportResultRow
import com.morpheusdata.model.ContentSecurityPolicy
import com.morpheusdata.views.HTMLResponse
import com.morpheusdata.views.ViewModel
import com.morpheusdata.response.ServiceResponse
import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import io.reactivex.Observable;

import java.sql.Connection

/**
 * A Sample Custom Report that lists all instances as well as their current status
 * This report does not take into account user permissions currently and simply uses a direct SQL Connection using Groovy SQL with RxJava to generate a set of results.
 *
 * A renderer is also defined to render the HTML via Handlebars templates.
 *
 * @author David Estes
 */
@Slf4j
class CustomReportProvider extends AbstractReportProvider {
	Plugin plugin
	MorpheusContext morpheusContext

	CustomReportProvider(Plugin plugin, MorpheusContext context) {
		this.plugin = plugin
		this.morpheusContext = context
	}

	@Override
	MorpheusContext getMorpheus() {
		morpheusContext
	}

	@Override
	Plugin getPlugin() {
		plugin
	}

	@Override
	String getCode() {
		'custom-report-account-price-history'
	}

	@Override
	String getName() {
		'Report - Account Price History'
	}

	ServiceResponse validateOptions(Map opts) {
		return ServiceResponse.success()
	}

	/**
	 * Demonstrates building a TaskConfig to get details about the Instance and renders the html from the specified template.
	 * @param instance details of an Instance
	 * @return
	 */
	@Override
	HTMLResponse renderTemplate(ReportResult reportResult, Map<String, List<ReportResultRow>> reportRowsBySection) {
		ViewModel<String> model = new ViewModel<String>()
		model.object = reportRowsBySection
		getRenderer().renderTemplate("hbs/instanceReport", model)
	}

	/**
	 * Allows various sources used in the template to be loaded
	 * @return
	 */
	@Override
	ContentSecurityPolicy getContentSecurityPolicy() {
		def csp = new ContentSecurityPolicy()
		csp.scriptSrc = '*.jsdelivr.net'
		csp.frameSrc = '*.digitalocean.com'
		csp.imgSrc = '*.wikimedia.org'
		csp.styleSrc = 'https: *.bootstrapcdn.com'
		csp
	}


	void process(ReportResult reportResult) {
		morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.generating).blockingGet();
		Long displayOrder = 0
		List<GroovyRowResult> results = []
		withDbConnection { Connection dbConnection ->
			if(reportResult.configMap?.phrase) {
				String phraseMatch = "${reportResult.configMap?.phrase}%"
				results = new Sql(dbConnection).rows("select id,name,hostname,os_type,server_type,internal_ip,external_ip,ssh_host,max_storage,used_storage,max_cpu,used_cpu,max_memory,used_memory,power_state,account_id,zone_id from compute_server where zone_id in (select zone_id from compute_zone where name like ${phraseMatch} group by name having count(*) > 0 ) order by zone_id;")
			} else {
				results = new Sql(dbConnection).rows("select id,name,hostname,os_type,server_type,internal_ip,external_ip,ssh_host,max_storage,used_storage,max_cpu,used_cpu,max_memory,used_memory,power_state,account_id,zone_id from compute_server where zone_id in (select zone_id from compute_zone where name like "%DG%" group by name having count(*) > 0 ) order by zone_id;")
			}
		}


		log.info("Results: ${results}")
		Observable<GroovyRowResult> observable = Observable.fromIterable(results) as Observable<GroovyRowResult>
		observable.map{ resultRow ->
			log.info("Mapping resultRow ${resultRow}")
			Map<String,Object> data = [id: resultRow.id, name: resultRow.name.toString(), hostname: resultRow.hostname.toString(), os_type: resultRow.os_type.toString(), server_type: resultRow.server_type.toString(), internal_ip: resultRow.internal_ip.toString(), external_ip: resultRow.external_ip.toString(), max_storage: resultRow.max_storage.toString(), used_storage: resultRow.used_storage.toString(), max_cpu: resultRow.max_cpu.toString(), used_cpu: resultRow.used_cpu.toString(), max_memory: resultRow.max_memory.toString(), used_memory: resultRow.used_memory.toString(),power_state: resultRow.power_state.toString(),account_id: resultRow.account_id.toString(),zone_id: resultRow.zone_id.toString()]
			ReportResultRow resultRowRecord = new ReportResultRow(section: ReportResultRow.SECTION_MAIN, displayOrder: displayOrder++, dataMap: data)
			log.info("resultRowRecord: ${resultRowRecord.dump()}")
			return resultRowRecord
		}.buffer(50).doOnComplete {
			morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.ready).blockingGet();
		}.doOnError { Throwable t ->
			morpheus.report.updateReportResultStatus(reportResult,ReportResult.Status.failed).blockingGet();
		}.subscribe {resultRows ->
			morpheus.report.appendResultRows(reportResult,resultRows).blockingGet()
		}

	}

	@Override
	String getDescription() {
		return "Provide Account Price History. Created by Deepti Gaharwar"
	}

	@Override
	String getCategory() {
		return 'inventory'
	}

	@Override
	Boolean getOwnerOnly() {
		return false
	}

	@Override
	Boolean getMasterOnly() {
		return true
	}

	@Override
	Boolean getSupportsAllZoneTypes() {
		return true
	}

	@Override
	List<OptionType> getOptionTypes() {
		[new OptionType(code: 'status-report-search', name: 'Search', fieldName: 'phrase', fieldContext: 'config', fieldLabel: 'Search Phrase', displayOrder: 0)]
	}
}
