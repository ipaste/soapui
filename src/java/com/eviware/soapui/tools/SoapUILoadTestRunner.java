/*
 *  soapUI, copyright (C) 2004-2009 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.loadtest.WsdlLoadTest;
import com.eviware.soapui.impl.wsdl.loadtest.data.actions.ExportLoadTestLogAction;
import com.eviware.soapui.impl.wsdl.loadtest.data.actions.ExportStatisticsAction;
import com.eviware.soapui.impl.wsdl.loadtest.log.LoadTestLog;
import com.eviware.soapui.impl.wsdl.loadtest.log.LoadTestLogEntry;
import com.eviware.soapui.model.project.ProjectFactoryRegistry;
import com.eviware.soapui.model.testsuite.LoadTestRunContext;
import com.eviware.soapui.model.testsuite.LoadTestRunListener;
import com.eviware.soapui.model.testsuite.LoadTestRunner;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestRunContext;
import com.eviware.soapui.model.testsuite.TestRunner;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.TestStepResult;
import com.eviware.soapui.model.testsuite.TestSuite;
import com.eviware.soapui.settings.UISettings;
import com.eviware.soapui.support.SoapUIException;
import com.eviware.soapui.support.StringUtils;

/**
 * Standalone test-runner used from maven-plugin, can also be used from
 * command-line (see xdocs) or directly from other classes.
 * <p>
 * For standalone usage, set the project file (with setProjectFile) and other
 * desired properties before calling run
 * </p>
 * 
 * @author Ole.Matzura
 */

public class SoapUILoadTestRunner extends AbstractSoapUITestRunner implements LoadTestRunListener
{
	private String testSuite;
	private String testCase;
	private String loadTest;
	private boolean printReport;
	private List<LoadTestRunner> failedTests = new ArrayList<LoadTestRunner>();
	private int testCaseCount;
	private int loadTestCount;
	private int limit = -1;
	private long threadCount = -1;
	private boolean saveAfterRun;

	public static String TITLE = "soapUI " + SoapUI.SOAPUI_VERSION + " LoadTest Runner";

	/**
	 * Runs the loadtests in the specified soapUI project file, see soapUI xdocs
	 * for details.
	 * 
	 * @param args
	 * @throws Exception
	 */

	public static void main( String[] args )
	{
		new SoapUILoadTestRunner().runFromCommandLine( args );
	}

	protected boolean processCommandLine( CommandLine cmd )
	{
		if( cmd.hasOption( "e" ) )
			setEndpoint( cmd.getOptionValue( "e" ) );

		if( cmd.hasOption( "s" ) )
			setTestSuite( getCommandLineOptionSubstSpace( cmd, "s" ) );

		if( cmd.hasOption( "c" ) )
			setTestCase( cmd.getOptionValue( "c" ) );

		if( cmd.hasOption( "l" ) )
			setLoadTest( cmd.getOptionValue( "l" ) );

		if( cmd.hasOption( "u" ) )
			setUsername( cmd.getOptionValue( "u" ) );

		if( cmd.hasOption( "p" ) )
			setPassword( cmd.getOptionValue( "p" ) );

		if( cmd.hasOption( "w" ) )
			setWssPasswordType( cmd.getOptionValue( "w" ) );

		if( cmd.hasOption( "d" ) )
			setDomain( cmd.getOptionValue( "d" ) );

		if( cmd.hasOption( "h" ) )
			setHost( cmd.getOptionValue( "h" ) );

		if( cmd.hasOption( "m" ) )
			setLimit( Integer.parseInt( cmd.getOptionValue( "m" ) ) );

		if( cmd.hasOption( "n" ) )
			setThreadCount( Integer.parseInt( cmd.getOptionValue( "n" ) ) );

		if( cmd.hasOption( "f" ) )
			setOutputFolder( getCommandLineOptionSubstSpace( cmd, "f" ) );

		if( cmd.hasOption( "t" ) )
			setSettingsFile( getCommandLineOptionSubstSpace( cmd, "t" ) );

		setPrintReport( cmd.hasOption( "r" ) );
		setSaveAfterRun( cmd.hasOption( "S" ) );

		if( cmd.hasOption( "x" ) )
		{
			setProjectPassword( cmd.getOptionValue( "x" ) );
		}

		if( cmd.hasOption( "v" ) )
		{
			setSoapUISettingsPassword( cmd.getOptionValue( "v" ) );
		}

		if( cmd.hasOption( "D" ) )
		{
			setSystemProperties( cmd.getOptionValues( "D" ) );
		}

		if( cmd.hasOption( "G" ) )
		{
			setGlobalProperties( cmd.getOptionValues( "G" ) );
		}

		if( cmd.hasOption( "P" ) )
		{
			setProjectProperties( cmd.getOptionValues( "P" ) );
		}

		return true;
	}

	public void setLimit( int limit )
	{
		this.limit = limit;
	}

	public void setThreadCount( long threadCount )
	{
		this.threadCount = threadCount;
	}

	protected SoapUIOptions initCommandLineOptions()
	{
		SoapUIOptions options = new SoapUIOptions( "loadtestrunner" );
		options.addOption( "e", true, "Sets the endpoint" );
		options.addOption( "s", true, "Sets the testsuite" );
		options.addOption( "c", true, "Sets the testcase" );
		options.addOption( "l", true, "Sets the loadtest" );
		options.addOption( "u", true, "Sets the username" );
		options.addOption( "p", true, "Sets the password" );
		options.addOption( "w", true, "Sets the WSS password type, either 'Text' or 'Digest'" );
		options.addOption( "d", true, "Sets the domain" );
		options.addOption( "h", true, "Sets the host" );
		options.addOption( "m", true, "Overrides the LoadTest Limit" );
		options.addOption( "n", true, "Overrides the LoadTest ThreadCount" );
		options.addOption( "r", false, "Exports statistics and testlogs for each LoadTest run" );
		options.addOption( "f", true, "Sets the output folder to export to" );
		options.addOption( "t", true, "Sets the soapui-settings.xml file to use" );
		options.addOption( "x", true, "Sets project password for decryption if project is encrypted" );
		options.addOption( "v", true, "Sets password for soapui-settings.xml file" );
		options.addOption( "D", true, "Sets system property with name=value" );
		options.addOption( "G", true, "Sets global property with name=value" );
		options.addOption( "P", true, "Sets or overrides project property with name=value" );
		options.addOption( "S", false, "Saves the project after running the tests" );

		return options;
	}

	public SoapUILoadTestRunner()
	{
		this( TITLE );
	}

	public SoapUILoadTestRunner( String title )
	{
		super( title );
	}

	public void setLoadTest( String loadTest )
	{
		this.loadTest = loadTest;
	}

	public void setPrintReport( boolean printReport )
	{
		this.printReport = printReport;
	}

	public void setSaveAfterRun( boolean saveAfterRun )
	{
		this.saveAfterRun = saveAfterRun;
	}

	/**
	 * Runs the testcases as configured with setXXX methods
	 * 
	 * @throws Exception
	 *            thrown if any tests fail
	 */

	public boolean runRunner() throws Exception
	{
		if( SoapUI.getSettings().getBoolean( UISettings.DONT_DISABLE_GROOVY_LOG ) )
		{
			initGroovyLog();
		}

		String projectFile = getProjectFile();

		// WsdlProject project = new WsdlProject( projectFile,
		// getProjectPassword() );
		WsdlProject project = ( WsdlProject )ProjectFactoryRegistry.getProjectFactory( "wsdl" ).createNew( projectFile,
				getProjectPassword() );

		if( project.isDisabled() )
			throw new Exception( "Failed to load soapUI project file [" + projectFile + "]" );

		initProjectProperties( project );

		int suiteCount = 0;

		for( int c = 0; c < project.getTestSuiteCount(); c++ )
		{
			if( testSuite == null || project.getTestSuiteAt( c ).getName().equalsIgnoreCase( testSuite ) )
			{
				runSuite( project.getTestSuiteAt( c ) );
				suiteCount++ ;
			}
		}

		if( suiteCount == 0 )
		{
			log.warn( "No test-suites matched argument [" + testSuite + "]" );
		}
		else if( testCaseCount == 0 )
		{
			log.warn( "No test-cases matched argument [" + testCase + "]" );
		}
		else if( loadTestCount == 0 )
		{
			log.warn( "No load-tests matched argument [" + loadTest + "]" );
		}
		else
		{
			if( saveAfterRun && !project.isRemote() )
			{
				try
				{
					project.save();
				}
				catch( Throwable t )
				{
					log.error( "Failed to save project", t );
				}
			}

			if( !failedTests.isEmpty() )
			{
				log.info( failedTests.size() + " load tests failed:" );
				for( LoadTestRunner loadTestRunner : failedTests )
				{
					log.info( loadTestRunner.getLoadTest().getName() + ": " + loadTestRunner.getReason() );
				}

				throw new SoapUIException( "LoadTests failed" );
			}
		}

		return true;
	}

	/**
	 * Run tests in the specified TestSuite
	 * 
	 * @param suite
	 *           the TestSuite to run
	 */

	public void runSuite( TestSuite suite )
	{
		long start = System.currentTimeMillis();
		for( int c = 0; c < suite.getTestCaseCount(); c++ )
		{
			String name = suite.getTestCaseAt( c ).getName();
			if( testCase == null || name.equalsIgnoreCase( testCase ) )
			{
				runTestCase( suite.getTestCaseAt( c ) );
				testCaseCount++ ;
			}
			else
				log.info( "Skipping testcase [" + name + "], filter is [" + testCase + "]" );
		}
		log.info( "soapUI suite [" + suite.getName() + "] finished in " + ( System.currentTimeMillis() - start ) + "ms" );
	}

	/**
	 * Runs the specified TestCase
	 * 
	 * @param testCase
	 *           the testcase to run
	 */

	private void runTestCase( TestCase testCase )
	{
		for( int c = 0; c < testCase.getLoadTestCount(); c++ )
		{
			String name = testCase.getLoadTestAt( c ).getName();
			if( loadTest == null || loadTest.equalsIgnoreCase( name ) )
			{
				runWsdlLoadTest( ( WsdlLoadTest )testCase.getLoadTestAt( c ) );
				loadTestCount++ ;
			}
		}
	}

	/**
	 * Runs the specified LoadTest
	 * 
	 * @param loadTest
	 *           the loadTest to run
	 */

	private void runWsdlLoadTest( WsdlLoadTest loadTest )
	{
		try
		{
			log.info( "Running LoadTest [" + loadTest.getName() + "]" );
			if( limit >= 0 )
			{
				log.info( "Overriding limit [" + loadTest.getTestLimit() + "] with specified [" + limit + "]" );
				loadTest.setTestLimit( limit );
			}

			if( threadCount >= 0 )
			{
				log
						.info( "Overriding threadCount [" + loadTest.getThreadCount() + "] with specified [" + threadCount
								+ "]" );
				loadTest.setThreadCount( threadCount );
			}

			loadTest.addLoadTestRunListener( this );
			LoadTestRunner runner = loadTest.run();

			// wait for test to finish
			while( runner.getStatus() == LoadTestRunner.Status.RUNNING )
			{
				log.info( "LoadTest [" + loadTest.getName() + "] progress: " + runner.getProgress() + ", "
						+ runner.getRunningThreadCount() );
				Thread.sleep( 1000 );
			}

			log.info( "LoadTest [" + loadTest.getName() + "] finished with status " + runner.getStatus().toString() );

			if( printReport )
			{
				log.info( "Exporting log and statistics for LoadTest [" + loadTest.getName() + "]" );

				loadTest.getStatisticsModel().finish();

				exportLog( loadTest );
				exportStatistics( loadTest );
			}
		}
		catch( Exception e )
		{
			SoapUI.logError( e );
			log.error( e );
		}
	}

	private void exportStatistics( WsdlLoadTest loadTest ) throws IOException
	{
		ExportStatisticsAction exportStatisticsAction = new ExportStatisticsAction( loadTest.getStatisticsModel() );
		String statisticsFileName = StringUtils.createFileName( loadTest.getName(), '_' ) + "-statistics.txt";
		if( getOutputFolder() != null )
		{
			ensureOutputFolder( loadTest );
			statisticsFileName = getAbsoluteOutputFolder( loadTest ) + File.separator + statisticsFileName;
		}

		int cnt = exportStatisticsAction.exportToFile( new File( statisticsFileName ) );
		log.info( "Exported " + cnt + " statistics to [" + statisticsFileName + "]" );
	}

	private void exportLog( WsdlLoadTest loadTest ) throws IOException
	{
		// export log first
		LoadTestLog loadTestLog = loadTest.getLoadTestLog();
		ExportLoadTestLogAction exportLoadTestLogAction = new ExportLoadTestLogAction( loadTestLog, null );
		String logFileName = StringUtils.createFileName( loadTest.getName(), '_' ) + "-log.txt";
		if( getOutputFolder() != null )
		{
			ensureOutputFolder( loadTest );
			logFileName = getAbsoluteOutputFolder( loadTest ) + File.separator + logFileName;
		}

		int cnt = exportLoadTestLogAction.exportToFile( new File( logFileName ) );
		log.info( "Exported " + cnt + " log items to [" + logFileName + "]" );

		int errorCnt = 0;
		for( int c = 0; c < loadTestLog.getSize(); c++ )
		{
			LoadTestLogEntry entry = ( LoadTestLogEntry )loadTestLog.getElementAt( c );

			if( entry != null && entry.isError() )
			{
				String entryFileName = StringUtils.createFileName( loadTest.getName(), '_' ) + "-error-" + errorCnt++
						+ "-entry.txt";
				if( getOutputFolder() != null )
				{
					ensureOutputFolder( loadTest );
					entryFileName = getAbsoluteOutputFolder( loadTest ) + File.separator + entryFileName;
				}

				try
				{
					entry.exportToFile( entryFileName );
				}
				catch( Exception e )
				{
					SoapUI.logError( e );
				}
			}
		}
		log.info( "Exported " + errorCnt + " error results" );
	}

	/**
	 * Sets the testcase to run
	 * 
	 * @param testCase
	 *           the testcase to run
	 */

	public void setTestCase( String testCase )
	{
		this.testCase = testCase;
	}

	/**
	 * Sets the TestSuite to run. If not set all TestSuites in the specified
	 * project file are run
	 * 
	 * @param testSuite
	 *           the testSuite to run.
	 */

	public void setTestSuite( String testSuite )
	{
		this.testSuite = testSuite;
	}

	public void afterLoadTest( LoadTestRunner loadTestRunner, LoadTestRunContext context )
	{
		if( loadTestRunner.getStatus() == LoadTestRunner.Status.FAILED )
		{
			failedTests.add( loadTestRunner );
		}
	}

	public void afterTestCase( LoadTestRunner loadTestRunner, LoadTestRunContext context, TestRunner testRunner,
			TestRunContext runContext )
	{
	}

	public void afterTestStep( LoadTestRunner loadTestRunner, LoadTestRunContext context, TestRunner testRunner,
			TestRunContext runContext, TestStepResult testStepResult )
	{
		super.afterStep( testRunner, runContext, testStepResult );
	}

	public void beforeLoadTest( LoadTestRunner loadTestRunner, LoadTestRunContext context )
	{
	}

	public void beforeTestCase( LoadTestRunner loadTestRunner, LoadTestRunContext context, TestRunner testRunner,
			TestRunContext runContext )
	{
	}

	public void beforeTestStep( LoadTestRunner loadTestRunner, LoadTestRunContext context, TestRunner testRunner,
			TestRunContext runContext, TestStep testStep )
	{
		super.beforeStep( testRunner, runContext );
	}

	public void loadTestStarted( LoadTestRunner loadTestRunner, LoadTestRunContext context )
	{
	}

	public void loadTestStopped( LoadTestRunner loadTestRunner, LoadTestRunContext context )
	{
	}
}