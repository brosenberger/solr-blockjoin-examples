package at.bro.code.solr.blockjoins;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.core.query.SimpleQuery;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import at.bro.code.solr.blockjoins.SolRApplication;
import at.bro.code.solr.utils.SolrUtils;

@Test
public class SolRBlockJoinSearchTest {

    private SolrTemplate solrTemplate;

    @BeforeClass
    void setup() {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(SolRApplication.class);
        context.refresh();
        solrTemplate = context.getBean(SolrTemplate.class);

        // remove all old documents
        solrTemplate.delete(new SimpleQuery("*:*"));

        // create new documents
        saveDocument(solrTemplate, "dad", "son", "daughter", "dandy");
        saveDocument(solrTemplate, "mum", "daughter", "girly");
        saveDocument(solrTemplate, "mother", "girl");
    }

    @Test
    void testParentOnMStarChildDaughterAndChildFiltering() throws SolrServerException {
        final List<SolrDocument> foundDocuments = querySolr("+relation_s:daughter", "id:m*", "relation_s:d*");

        Assert.assertEquals(foundDocuments.size(), 1);
        Assert.assertEquals(foundDocuments.get(0).getFieldValue("id"), "mum");
        Assert.assertEquals(foundDocuments.get(0).getChildDocuments().size(), 1);
        Assert.assertEquals(foundDocuments.get(0).getChildDocuments().get(0).get("relation_s"), "daughter");
    }

    @Test
    void testParentOnMStarChildDaughterAndNoChildFiltering() throws SolrServerException {
        final List<SolrDocument> foundDocuments = querySolr("+relation_s:daughter", "id:m*", null);

        Assert.assertEquals(foundDocuments.size(), 1);
        Assert.assertEquals(foundDocuments.get(0).getFieldValue("id"), "mum");
        Assert.assertEquals(foundDocuments.get(0).getChildDocuments().size(), 2);
    }

    @Test
    void testParentNoRestrictionChildDaughterAndNoChildFiltering() throws SolrServerException {
        final List<SolrDocument> foundDocuments = querySolr("+relation_s:daughter", null, null);

        Assert.assertEquals(foundDocuments.size(), 2);
        Assert.assertEquals(foundDocuments.get(0).getFieldValue("id"), "dad");
        Assert.assertEquals(foundDocuments.get(0).getChildDocuments().size(), 3);
        Assert.assertEquals(foundDocuments.get(1).getFieldValue("id"), "mum");
        Assert.assertEquals(foundDocuments.get(1).getChildDocuments().size(), 2);
    }

    @Test
    void testParentNoRestrictionChildDStartAndNoChildFiltering() throws SolrServerException {
        final List<SolrDocument> foundDocuments = querySolr("+relation_s:s*", null, null);

        Assert.assertEquals(foundDocuments.size(), 1);
        Assert.assertEquals(foundDocuments.get(0).getFieldValue("id"), "dad");
        Assert.assertEquals(foundDocuments.get(0).getChildDocuments().size(), 3);
    }

    /* *******
     * private helper methods
     */

    private List<SolrDocument> querySolr(String parentChildRestriction, String parentFilterRestriction,
            String childRestriction) throws SolrServerException {
        final ModifiableSolrParams params = baseParams(parentChildRestriction, parentFilterRestriction,
                childRestriction);
        return SolrUtils.expandResults(solrTemplate.getSolrServer().query(params), "id");
    }

    private static ModifiableSolrParams baseParams(String parentChildRestriction, String parentFilterRestriction,
            String childRestriction) {
        final ModifiableSolrParams params = new ModifiableSolrParams();
        params.add("q", "{!parent which=\'type_s:parent\'}" + parentChildRestriction);
        if (StringUtils.isNotBlank(parentFilterRestriction)) {
            params.add("fq", parentFilterRestriction);
        }
        params.add("expand", "true");
        params.add("expand.field", "_root_");
        params.add("expand.q", "*:*");
        if (StringUtils.isNotBlank(childRestriction)) {
            params.add("expand.fq", childRestriction);
        } else {
            params.add("expand.fq", "*:*");
        }
        return params;
    }

    private static void saveDocument(SolrTemplate solr, String id, String... children) {
        final SolrInputDocument document = new SolrInputDocument();
        document.addField("id", id);
        document.addField("name", id);
        document.addField("type_s", "parent");
        for (final String child : children) {
            final SolrInputDocument childDocument = new SolrInputDocument();
            childDocument.addField("id", child);
            childDocument.addField("relation_s", child);
            childDocument.addField("type_s", "child");
            document.addChildDocument(childDocument);
        }

        solr.saveDocument(document);
        solr.commit();
    }
}
