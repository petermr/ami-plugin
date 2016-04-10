package org.xmlcml.ami2.plugins;

import java.io.File;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.xmlcml.cmine.files.PluginOption;
import org.xmlcml.cmine.files.PluginSnippetsTree;
import org.xmlcml.cmine.files.ResultElement;
import org.xmlcml.cmine.files.SnippetsTree;
import org.xmlcml.cmine.files.XMLSnippets;
import org.xmlcml.cmine.util.CMineUtil;
import org.xmlcml.cmine.util.CellCalculator;
import org.xmlcml.cmine.util.CellRenderer;
import org.xmlcml.cmine.util.DataTablesTool;
import org.xmlcml.html.HtmlBr;
import org.xmlcml.html.HtmlDiv;
import org.xmlcml.html.HtmlElement;
import org.xmlcml.html.HtmlSpan;
import org.xmlcml.html.HtmlTable;
import org.xmlcml.html.HtmlTd;
import org.xmlcml.html.HtmlTr;
import org.xmlcml.xml.XMLUtil;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;

import nu.xom.Element;

public class ResultsAnalysis implements CellCalculator {

	private static final Logger LOG = Logger.getLogger(ResultsAnalysis.class);

	static {
		LOG.setLevel(Level.DEBUG);
	}
	
	public enum SummaryType {
		COMMONEST("commonest"),
		CONTEXT("context"),
		COUNT("count"),
		ENTRIES("entries"),
		FULL("full");
		String type;
		private SummaryType(String type) {
			this.type = type;
		}
		public String toString() {
			return type;
		}
	}
	
	public static final String SCHOLARLY_HTML = "/scholarly.html";
	protected static final String SNIPPETS_XML = "snippets.xml";
	public static List<SummaryType> SUMMARY_TYPES = 
		Arrays.asList(new SummaryType[]{
				SummaryType.COMMONEST, 
				SummaryType.COUNT, 
				SummaryType.CONTEXT, 
				SummaryType.ENTRIES, 
				SummaryType.FULL});
	
	public Map<String, PluginSnippetsTree> pluginSnippetsTreeByPluginOption;
	private Set<String> cTreeNameSet;
	public List<String> cTreeNameList;
	public List<String> pluginOptionNameList;
	private DataTablesTool dataTablesTool;
	private String rowHeadingName;

	@Deprecated
	private List<SummaryType> cellContentFlags;
	private SummaryType cellContentFlag;
	private CellRenderer currentCellRenderer;
	
	public ResultsAnalysis() {
	}
		
	public ResultsAnalysis(DataTablesTool dataTablesTool) {
		this.dataTablesTool = dataTablesTool;
		dataTablesTool.setCellCalculator(this);
	}
	
	public void addSnippetsFile(File xmlFile) {
		
		if (xmlFile == null) {
			LOG.error("Null XML file");
			return;
		}
		Element element = null;
		try {
			element = XMLUtil.parseQuietlyToDocument(new FileInputStream(xmlFile)).getRootElement();
		} catch (FileNotFoundException e) {
			LOG.error("Non-existent XML file: "+xmlFile);
			return;
		}
		PluginSnippetsTree pluginSnippetsTree = PluginSnippetsTree.createPluginSnippetsTree(element);
		if (pluginSnippetsTree == null) {
			LOG.warn("Cannot create PluginSnippetsTree: "+xmlFile);
			return;
		}
		ensurePluginSnippetsTreeByPluginOptionName();
		PluginOption pluginOption = pluginSnippetsTree.getPluginOption();
		if (pluginOption == null) {
			LOG.warn("Null pluginOption");
			return;
		}
		if (pluginSnippetsTreeByPluginOption.containsKey(pluginOption)) {
			LOG.warn("Already has pluginOption: "+pluginOption);
		}
		pluginSnippetsTreeByPluginOption.put(pluginOption.toString(), pluginSnippetsTree);
	}
	
	private void ensurePluginSnippetsTreeByPluginOptionName() {
		if (pluginSnippetsTreeByPluginOption == null) {
			pluginSnippetsTreeByPluginOption = new HashMap<String, PluginSnippetsTree>();
		}
	}

	public Map<String, PluginSnippetsTree> getPluginSnippetsTreeByPluginOption() {
		return pluginSnippetsTreeByPluginOption;
	}

	public Set<String> getCTreeNameSet() {
		if (cTreeNameSet == null) {
			cTreeNameSet = new HashSet<String>();
			ensurePluginSnippetsTreeByPluginOptionName();
			for (Map.Entry<String, PluginSnippetsTree> entry : pluginSnippetsTreeByPluginOption.entrySet()) {
				cTreeNameSet.addAll(entry.getValue().getCTreeNameList());
			}
		}
		return cTreeNameSet;
	}

	public List<String> getSortedCTreeNameList() {
		getCTreeNameSet();
		cTreeNameList = Arrays.asList(cTreeNameSet.toArray(new String[0]));
		Collections.sort(cTreeNameList);
		return cTreeNameList;
	}

	public HtmlElement createSnippetsTreeContents(SnippetsTree snippetsTree) {
		HtmlElement htmlElement = null;
		List<XMLSnippets> list = snippetsTree.getOrCreateSnippetsList();
		List<String> terms = createTerms(list);
		List<HtmlDiv> ptpDivs = createPreTermPost(list);
		Multiset<String> termMultiset = createMultisetOmittingNullEntries(terms);
		Iterable<Multiset.Entry<String>> entrys = CMineUtil.getEntriesSortedByCount(termMultiset);
		Iterator<Entry<String>> iterator = entrys.iterator();
		if (cellContentFlag == null) {
			LOG.warn("no cell content flag");
		} else if (cellContentFlag.equals(SummaryType.FULL)) {
			htmlElement = new HtmlDiv();
			htmlElement.setTitle(String.valueOf(terms.size()));
			int maxCount = 4;
			for (Entry<String> entry : entrys) {
				HtmlElement td = createSpan(entry);
				htmlElement.appendChild(td);
				if (maxCount-- <= 0) {
					break;
				}
				htmlElement.appendChild(new HtmlBr());
			}
		} else if (cellContentFlag.equals(SummaryType.COMMONEST)) {
			if (iterator.hasNext()) {
				Entry<String> entry = iterator.next();
				htmlElement = createSpan(entry);
			}
		} else if (cellContentFlag.equals(SummaryType.CONTEXT)) {
			htmlElement = new HtmlDiv();
			int maxCount = 3;
			for (HtmlDiv ptp : ptpDivs) {
				htmlElement.appendChild(ptp);
				if (maxCount-- <= 0) {
					break;
				}
			}
		} else if (cellContentFlag.equals(SummaryType.COUNT)) {
			htmlElement = createSpan(termMultiset.size());
		} else if (cellContentFlag.equals(SummaryType.ENTRIES)) {
			htmlElement = createSpan(termMultiset.entrySet().size());
		} else {
			LOG.warn("Unknown flag: "+cellContentFlag);
		}
		return htmlElement;
	}

	private Multiset<String> createMultisetOmittingNullEntries(List<String> terms) {
		Multiset<String> multiset = HashMultiset.create();
		for (String term : terms) {
			if (term == null) {
				term = "NULL";
			}
			multiset.add(term);
		}
		return multiset;
	}

	private HtmlElement createSpan(int size) {
		HtmlElement htmlElement;
		String ss = String.valueOf(size);
		htmlElement = new HtmlSpan();
		htmlElement.appendChild(ss);
		return htmlElement;
	}

	private HtmlElement createSpan(Entry<String> entry) {
		HtmlElement htmlSpan = new HtmlSpan();
		String value = entry.getElement().toString();
		if (currentCellRenderer == null) {
			htmlSpan.appendChild(value);
		} else {
			currentCellRenderer.setValue(value);
			htmlSpan.appendChild(currentCellRenderer.getHtmlElement());
		}
		int count = entry.getCount();
		if (count > 1) {
			htmlSpan.appendChild(" x "+count);
		}
		return htmlSpan;
	}

	private List<HtmlDiv> createPreTermPost(List<XMLSnippets> list) {
		List<HtmlDiv> preTermPosts = new ArrayList<HtmlDiv>();
		for (XMLSnippets snippets : list) {
			for (int i = 0; i < snippets.getChildElements().size(); i++) {
				ResultElement resultElement = ResultElement.createResultElement(snippets.getChildElements().get(i));
				String term = resultElement.getTerm();
				String pre = resultElement.getPre();
				String post = resultElement.getPost();
				HtmlDiv div = new HtmlDiv();
				div.appendChild(createSpan(" ", pre));
				div.appendChild(createSpan(term, pre+"~"+term+"~"+post));
				div.appendChild(createSpan(" ", post));
				preTermPosts.add(div);
			}
		}
		return preTermPosts;
	}

	private HtmlSpan createSpan(String content, String title) {
		HtmlSpan span = new HtmlSpan();
		span.setValue(content);
		if (title != null) {
			span.setTitle(title);
		}
		return span;
	}
	
	private List<String> createTerms(List<XMLSnippets> list) {
		List<String> terms = new ArrayList<String>();
		for (XMLSnippets snippets : list) {
			for (int i = 0; i < snippets.getChildElements().size(); i++) {
				ResultElement resultElement = ResultElement.createResultElement(snippets.getChildElements().get(i));
				terms.add(resultElement.getTerm());
			}
		}
		return terms;
	}

	public HtmlTable makeHtmlDataTable() {
		ensurePluginSnippetsTreeByPluginOptionName();
		Set<String> set = this.pluginSnippetsTreeByPluginOption.keySet();
		LOG.trace("HTML"+set+" | "+pluginSnippetsTreeByPluginOption.size());
		pluginOptionNameList = Arrays.asList(set.toArray(new String[0]));
		Collections.sort(this.pluginOptionNameList);
		
		List<CellRenderer> cellRendererList = new ArrayList<CellRenderer>();
		AMIPluginOptionList pluginOptionList = new AMIPluginOptionList();
		for (String pluginOptionName : pluginOptionNameList) {
			AMIPluginOption pluginOption = pluginOptionList.getPluginOption(pluginOptionName);
			if (pluginOption == null) {
				throw new RuntimeException("Cannot find plugin: "+pluginOptionName);
			}
			CellRenderer cellRenderer = pluginOption.getNewCellRenderer();
			cellRenderer.setFlag(pluginOptionName);
			cellRendererList.add(cellRenderer);
		}
		LOG.trace("CELL "+cellRendererList);
		dataTablesTool.setCellRendererList(cellRendererList);
		dataTablesTool.setRowHeadingList(this.getSortedCTreeNameList());
		
		HtmlTable htmlTable = dataTablesTool.createHtmlDataTable();
		htmlTable.setClassAttribute(DataTablesTool.TABLE+" "+DataTablesTool.TABLE_STRIPED+" "+DataTablesTool.TABLE_BORDERED+" "+DataTablesTool.TABLE_HOVER);
		htmlTable.setId(dataTablesTool.getId());

		return htmlTable;
	}

	public CellCalculator setLocalLink0(String link0) {
		dataTablesTool.setLocalLink0(link0);
		return this;
	}

	public CellCalculator setLocalLink1(String link1) {
		dataTablesTool.setLocalLink1(link1);
		return this;
	}

	public CellCalculator setRemoteLink0(String link0) {
		dataTablesTool.setRemoteLink0(link0);
		return this;
	}

	public CellCalculator setRemoteLink1(String link1) {
		dataTablesTool.setRemoteLink1(link1);
		return this;
	}

	public ResultsAnalysis setRowHeadingName(String rowHeadingName) {
		this.rowHeadingName = rowHeadingName;
		return this;
	}

	public String getRowHeadingName() {
		return this.rowHeadingName;
	}

	public DataTablesTool getDataTablesTool() {
		return dataTablesTool;
	}

	public void addCellValues(List<CellRenderer> columnHeadingList, HtmlTr htmlTr, int iRow) {
		for (int iCol = 0; iCol < columnHeadingList.size(); iCol++) {
			this.currentCellRenderer = columnHeadingList.get(iCol);
			HtmlElement htmlElement = createCellContents(iRow, iCol);
			HtmlElement htmlTd = new HtmlTd();
			if (htmlElement != null) {
				htmlTd.appendChild(htmlElement);
			}
			htmlTr.appendChild(htmlTd);
		}
	}

	public List<String> getColumnHeadingList() {
		return pluginOptionNameList;
	}

	public List<String> getRowHeadingList() {
		return cTreeNameList;
	}

	public HtmlElement createCellContents(int iRow, int iCol) {
		String columnHeadingx = getColumnHeadingList().get(iCol);
		PluginSnippetsTree pluginSnippetsTree = this.pluginSnippetsTreeByPluginOption.get(columnHeadingx);
		String rowHeading = getRowHeadingList().get(iRow);
		SnippetsTree snippetsTree = pluginSnippetsTree.getOrCreateSnippetsTreeByCTreeName().get(rowHeading);
		HtmlElement contents =  (snippetsTree == null) ? null : this.createSnippetsTreeContents(snippetsTree);
		return contents;
	}

	public void setCellContentFlags(List<SummaryType> flags) {
		cellContentFlags = flags;
	}

	public void setCellContentFlag(SummaryType flag) {
		cellContentFlag = flag;
	}

	public void addDefaultSnippets(File projectDir) {
		File[] snippetsFiles = projectDir.listFiles(new FilenameFilter() {
			
			public boolean accept(File dir, String name) {
				return name != null && name.endsWith(SNIPPETS_XML);
			}
		});
		if (snippetsFiles != null) {
			for (File file : snippetsFiles) {
				this.addSnippetsFile(file);
			}
		}
				
	}

	public void addSnippetsFiles(List<File> snippetsFiles) {
		for (File file : snippetsFiles) {
			this.addSnippetsFile(file);
		}
	}
	
}
