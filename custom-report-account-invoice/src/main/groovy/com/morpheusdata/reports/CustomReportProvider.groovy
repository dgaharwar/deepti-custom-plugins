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
		'custom-report-account-invoice'
	}

	@Override
	String getName() {
		'Report - Account Invoice'
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
				results = new Sql(dbConnection).rows("select id,date_created,compute_price,currency,total_price,storage_price,running_price,account_name,plan_id,ref_type,zone_name,last_updated,user_name from account_invoice where ref_name like ${phraseMatch} group by ref_name having count(*) > 1 order by id;")
			} else {
				results = new Sql(dbConnection).rows("select id,date_created,compute_price,currency,total_price,storage_price,running_price,account_name,plan_id,ref_type,zone_name,last_updated,user_name from account_invoice where ref_name like 'Instance%' group by ref_name having count(*) > 1 order by id;")
			}
		}


		log.info("Results: ${results}")
		Observable<GroovyRowResult> observable = Observable.fromIterable(results) as Observable<GroovyRowResult>
		observable.map{ resultRow ->
			log.info("Mapping resultRow ${resultRow}")
			Map<String,Object> data = [id: resultRow.id, date_created: resultRow.date_created.toString(), currency: resultRow.currency.toString(), total_price: resultRow.total_price.toString(), storage_price: resultRow.storage_price.toString(), running_price: resultRow.running_price.toString(), account_name: resultRow.account_name.toString(), plan_id: resultRow.plan_id.toString(), ref_type: resultRow.ref_type.toString(), zone_name: resultRow.zone_name.toString(), last_updated: resultRow.last_updated.toString(), user_name: resultRow.user_name.toString()]
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
		return "Provide Account Invoice. Created by Deepti Gaharwar"
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
