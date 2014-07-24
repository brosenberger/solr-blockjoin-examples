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
    private final List<Vehicle> availableVehicles = new ArrayList<>();

    @Override
    protected String getSolrServerId() {
        return SolRApplication.VEHICLES_SOLR;
    }

    @BeforeClass
    void setup() {
        // all dates are 2014-12-XX 00:00:00
        final Vehicle v1 = new Vehicle(1L, "audi", "a4", Arrays.asList(new Price(createDate(1), createDate(10), 1.,
                "linz"), new Price(createDate(11), createDate(15), 2., "linz"), new Price(createDate(9),
                        createDate(12), 1., "salzburg")), new Rating("linz", "A+"), new Rating("salzburg", "A"));
        saveDocument(v1);
        final Vehicle v2 = new Vehicle(2L, "audi", "a3", Arrays.asList(new Price(createDate(3), createDate(10), 1.,
                "linz"), new Price(createDate(11), createDate(15), 10., "linz"), new Price(createDate(9),
                        createDate(12), 12., "salzburg")), new Rating("linz", "A"));
        saveDocument(v2);
        final Vehicle v3 = new Vehicle(3L, "vw", "sharan", Arrays.asList(new Price(createDate(3), createDate(10), 1.,
                "linz"), new Price(createDate(11), createDate(15), 10., "linz"), new Price(createDate(9),
                        createDate(12), 1., "salzburg")), new Rating("linz", "B"), new Rating("salzburg", "A-"));
        saveDocument(v3);
        final Vehicle v4 = new Vehicle(4L, "vw", "golf", Arrays.asList(new Price(createDate(3), createDate(10), 1.,
                "wels")));
        saveDocument(v4);
        availableVehicles.add(v1);
        availableVehicles.add(v2);
        availableVehicles.add(v3);
        availableVehicles.add(v4);
    }

    @Test
    void testFindAllVehiclesInLinz() throws SolrServerException {
        final List<Vehicle> vehicles = querySolr("+location_s:linz", "", "location_s:linz");

        Assert.assertEquals(vehicles.size(), 3);
    }

    @Test
    void testFindAllCurrentPricesForVehicles() throws SolrServerException {
        final List<Vehicle> vehicles = querySolr("", "", prepareCurrentDateValidity("2014-12-4T12:00:00.999Z"));

        Assert.assertEquals(vehicles.size(), 4);
        for (final Vehicle v : vehicles) {
            Assert.assertEquals(v.getPrices().size(), 1);
            // no other child documents are fetched
            Assert.assertEquals(v.getRatings().isEmpty(), true);
        }
    }

    @Test
    void testFindMultipleChildrenWithoutRestrictions() throws SolrServerException {
        final List<Vehicle> vehicles = querySolr("", "", "");

        Assert.assertEquals(vehicles, availableVehicles);
    }

    @Test
    void testFindAllCurrentPricesAndLoadAllRatings() throws SolrServerException {
        final List<Vehicle> vehicles = querySolr("", "", "(type_s:" + PRICE_TYPE + " AND "
                + prepareCurrentDateValidity("2014-12-4T12:00:00.999Z") + ")OR(type_s:" + RATING_TYPE + ")");

        Assert.assertEquals(vehicles.size(), 4);
        boolean ratingsFetched = false;
        for (final Vehicle v : vehicles) {
            Assert.assertEquals(v.getPrices().size(), 1);
            ratingsFetched = ratingsFetched || !v.getRatings().isEmpty();
        }
        Assert.assertEquals(ratingsFetched, true, "there should be at least one vehicle with ratings");
    }

    /* *******
     * private helper methods
     */

    private String prepareCurrentDateValidity(String date) {
        final String dateValidityFormat = "validFrom_date:[* TO %1$s]&validTo_date:[%1$s TO *]";
        return String.format(dateValidityFormat, date);
    }

    private List<Vehicle> querySolr(String parentChildRestriction, String parentFilterRestriction,
            String childRestriction) throws SolrServerException {
        final ModifiableSolrParams params = baseParams(parentChildRestriction, parentFilterRestriction,
                childRestriction);

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
    private static final String RATING_TYPE = "rating";

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
        for (final Rating r : vehicle.getRatings()) {
            final SolrInputDocument child = new SolrInputDocument();
            child.addField("location_s", r.getLocation());
            child.addField("rating_s", r.getRating());
            child.addField("type_s", RATING_TYPE);

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
        final List<Rating> ratings = loadRatings(vDoc.getChildDocuments());

        return new Vehicle(id, brand, model, prices, ratings);
    }

    private List<Rating> loadRatings(List<SolrDocument> docs) {
        final List<Rating> ratings = new ArrayList<>();
        for (final SolrDocument d : docs) {
            if (RATING_TYPE.equals(d.get("type_s"))) {
                ratings.add(new Rating((String) d.get("location_s"), (String) d.get("rating_s")));
            }
        }
        return ratings;
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
        private final List<Rating> ratings;
        private final String model;
        private final String brand;

        @SuppressWarnings("unchecked")
        public Vehicle(Long id, String brand, String model, List<Price> prices, List<Rating> ratings) {
            this.id = id;
            this.model = model;
            this.brand = brand;
            this.prices = (List<Price>) ObjectUtils.defaultIfNull(prices, Collections.emptyList());
            this.ratings = (List<Rating>) ObjectUtils.defaultIfNull(ratings, Collections.emptyList());
        }

        public Vehicle(Long id, String brand, String model, List<Price> prices, Rating... ratings) {
            this(id, brand, model, prices, ratings == null ? null : Arrays.asList(ratings));
        }

        public Long getId() {
            return id;
        }

        public List<Price> getPrices() {
            return prices;
        }

        public List<Rating> getRatings() {
            return ratings;
        }

        public String getModel() {
            return model;
        }

        public String getBrand() {
            return brand;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((brand == null) ? 0 : brand.hashCode());
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((model == null) ? 0 : model.hashCode());
            result = prime * result + ((prices == null) ? 0 : prices.hashCode());
            result = prime * result + ((ratings == null) ? 0 : ratings.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Vehicle other = (Vehicle) obj;
            if (!getOuterType().equals(other.getOuterType())) {
                return false;
            }
            if (brand == null) {
                if (other.brand != null) {
                    return false;
                }
            } else if (!brand.equals(other.brand)) {
                return false;
            }
            if (id == null) {
                if (other.id != null) {
                    return false;
                }
            } else if (!id.equals(other.id)) {
                return false;
            }
            if (model == null) {
                if (other.model != null) {
                    return false;
                }
            } else if (!model.equals(other.model)) {
                return false;
            }
            if (prices == null) {
                if (other.prices != null) {
                    return false;
                }
            } else if (!prices.equals(other.prices)) {
                return false;
            }
            if (ratings == null) {
                if (other.ratings != null) {
                    return false;
                }
            } else if (!ratings.equals(other.ratings)) {
                return false;
            }
            return true;
        }

        private SolrBlockJoinSearchVehiclesTest getOuterType() {
            return SolrBlockJoinSearchVehiclesTest.this;
        }

    }

    private class Rating {
        private final String location;
        private final String rating;

        public Rating(String location, String rating) {
            this.location = location;
            this.rating = rating;
        }

        public String getLocation() {
            return location;
        }

        public String getRating() {
            return rating;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((location == null) ? 0 : location.hashCode());
            result = prime * result + ((rating == null) ? 0 : rating.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Rating other = (Rating) obj;
            if (!getOuterType().equals(other.getOuterType())) {
                return false;
            }
            if (location == null) {
                if (other.location != null) {
                    return false;
                }
            } else if (!location.equals(other.location)) {
                return false;
            }
            if (rating == null) {
                if (other.rating != null) {
                    return false;
                }
            } else if (!rating.equals(other.rating)) {
                return false;
            }
            return true;
        }

        private SolrBlockJoinSearchVehiclesTest getOuterType() {
            return SolrBlockJoinSearchVehiclesTest.this;
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

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((location == null) ? 0 : location.hashCode());
            result = prime * result + ((validFrom == null) ? 0 : validFrom.hashCode());
            result = prime * result + ((validTo == null) ? 0 : validTo.hashCode());
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Price other = (Price) obj;
            if (!getOuterType().equals(other.getOuterType())) {
                return false;
            }
            if (location == null) {
                if (other.location != null) {
                    return false;
                }
            } else if (!location.equals(other.location)) {
                return false;
            }
            if (validFrom == null) {
                if (other.validFrom != null) {
                    return false;
                }
            } else if (!validFrom.equals(other.validFrom)) {
                return false;
            }
            if (validTo == null) {
                if (other.validTo != null) {
                    return false;
                }
            } else if (!validTo.equals(other.validTo)) {
                return false;
            }
            if (value == null) {
                if (other.value != null) {
                    return false;
                }
            } else if (!value.equals(other.value)) {
                return false;
            }
            return true;
        }

        private SolrBlockJoinSearchVehiclesTest getOuterType() {
            return SolrBlockJoinSearchVehiclesTest.this;
        }

    }

}
