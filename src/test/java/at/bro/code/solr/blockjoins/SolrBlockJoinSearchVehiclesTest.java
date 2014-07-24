package at.bro.code.solr.blockjoins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.ObjectUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import at.bro.code.solr.utils.SolrUtils;

@Test
public class SolrBlockJoinSearchVehiclesTest extends BaseSolrBlockJoinTest {

    private final static Logger LOG = LoggerFactory.getLogger(SolrBlockJoinSearchVehiclesTest.class);

    @Override
    protected String getSolrServerId() {
        return SolRApplication.VEHICLES_SOLR;
    }

    @BeforeClass
    void setup() {
        // all dates are 2014-12-XX 00:00:00
        saveDocument(new Vehicle(1L, "audi", "a4", new Price(createDate(1), createDate(10), 1., "linz"), new Price(
                createDate(11), createDate(15), 2., "linz"), new Price(createDate(9), createDate(12), 1., "salzburg")));
        saveDocument(new Vehicle(2L, "audi", "a3", new Price(createDate(3), createDate(10), 1., "linz"), new Price(
                createDate(11), createDate(15), 10., "linz"), new Price(createDate(9), createDate(12), 12., "salzburg")));
        saveDocument(new Vehicle(3L, "vw", "sharan", new Price(createDate(3), createDate(10), 1., "linz"), new Price(
                createDate(11), createDate(15), 10., "linz"), new Price(createDate(9), createDate(12), 1., "salzburg")));
        saveDocument(new Vehicle(4L, "vw", "golf", new Price(createDate(3), createDate(10), 1., "wels")));
    }

    @Test
    void testFindAllVehiclesInLinz() throws SolrServerException {
        final List<Vehicle> vehicles = querySolr("+location_s:linz", "", "location_s:linz");

        Assert.assertEquals(vehicles.size(), 3);
    }

    @Test
    void testFindAllCurrentPricesForVehicles() throws SolrServerException {
        final String dateValidityFormat = "validFrom_date:[* TO %1$s]&validTo_date:[%1$s TO *]";
        final List<Vehicle> vehicles = querySolr("", "", String.format(dateValidityFormat, "2014-12-4T12:00:00.999Z"));

        Assert.assertEquals(vehicles.size(), 4);
        for (final Vehicle v : vehicles) {
            Assert.assertEquals(v.getPrices().size(), 1);
        }
    }

    /* *******
     * private helper methods
     */

    private List<Vehicle> querySolr(String parentChildRestriction, String parentFilterRestriction,
            String childRestriction) throws SolrServerException {
        final ModifiableSolrParams params = baseParams(parentChildRestriction, parentFilterRestriction,
                childRestriction);

        LOG.info("#############################");
        LOG.info("q = " + params.get("q"));

        final List<Vehicle> vehicles = new ArrayList<>();
        final List<SolrDocument> results = SolrUtils.expandResults(solrTemplate.getSolrServer().query(params), "id");
        for (final SolrDocument doc : results) {
            vehicles.add(loadVehicle(doc));
        }
        return vehicles;
    }

    private static ModifiableSolrParams baseParams(String parentChildRestriction, String parentFilterRestriction,
            String childRestriction) {
        return SolrUtils.baseBlockJoinParams(SolrUtils.prepareParentSelector("type_s:" + VEHICLE_TYPE)
                + parentChildRestriction, parentFilterRestriction, childRestriction);
    }

    private static final String VEHICLE_TYPE = "vehicle";
    private static final String PRICE_TYPE = "price";

    private void saveDocument(Vehicle vehicle) {
        final SolrInputDocument vDoc = new SolrInputDocument();
        vDoc.addField("id", vehicle.getId());
        vDoc.addField("model_s", vehicle.getModel());
        vDoc.addField("brand_s", vehicle.getBrand());
        vDoc.addField("type_s", VEHICLE_TYPE);

        for (final Price p : vehicle.getPrices()) {
            final SolrInputDocument child = new SolrInputDocument();
            child.addField("validFrom_date", p.getValidFrom());
            child.addField("validTo_date", p.getValidTo());
            child.addField("location_s", p.getLocation());
            child.addField("value_d", p.getValue().doubleValue());
            child.addField("type_s", PRICE_TYPE);

            vDoc.addChildDocument(child);
        }

        solrTemplate.saveDocument(vDoc);
        solrTemplate.commit();
    }

    private Vehicle loadVehicle(SolrDocument vDoc) {
        final Long id = Long.parseLong((String) vDoc.get("id"));
        final String model = (String) vDoc.get("model_s");
        final String brand = (String) vDoc.get("brand_s");

        final List<Price> prices = loadPrices(vDoc.getChildDocuments());

        return new Vehicle(id, brand, model, prices);
    }

    private List<Price> loadPrices(List<SolrDocument> docs) {
        final List<Price> prices = new ArrayList<>();
        for (final SolrDocument d : docs) {
            if (PRICE_TYPE.equals(d.get("type_s"))) {

                prices.add(new Price((Date) d.get("validFrom_date"), (Date) d.get("validTo_date"), (Double) d
                        .get("value_d"), (String) d.get("location_s")));
            }
        }
        return prices;
    }

    private class Vehicle {
        private final Long id;
        private final List<Price> prices;
        private final String model;
        private final String brand;

        @SuppressWarnings("unchecked")
        public Vehicle(Long id, String brand, String model, List<Price> prices) {
            this.id = id;
            this.model = model;
            this.brand = brand;
            this.prices = (List<Price>) ObjectUtils.defaultIfNull(prices, Collections.emptyList());
        }

        public Vehicle(Long id, String brand, String model, Price... prices) {
            this(id, brand, model, prices == null ? null : Arrays.asList(prices));
        }

        public Long getId() {
            return id;
        }

        public List<Price> getPrices() {
            return prices;
        }

        public String getModel() {
            return model;
        }

        public String getBrand() {
            return brand;
        }
    }

    private class Price {
        private final Date validFrom;
        private final Date validTo;
        private final Double value;
        private final String location;

        public Price(Date validFrom, Date validTo, Double value, String location) {
            this.validFrom = validFrom;
            this.validTo = validTo;
            this.value = value;
            this.location = location;
        }

        public Date getValidFrom() {
            return validFrom;
        }

        public Date getValidTo() {
            return validTo;
        }

        public Double getValue() {
            return value;
        }

        public String getLocation() {
            return location;
        }

    }

}
