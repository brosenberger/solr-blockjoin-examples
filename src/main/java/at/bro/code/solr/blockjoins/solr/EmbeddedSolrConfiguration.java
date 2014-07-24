package at.bro.code.solr.blockjoins.solr;

import javax.annotation.Resource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.server.support.EmbeddedSolrServerFactoryBean;

@Configuration
@PropertySource("classpath:application.properties")
public class EmbeddedSolrConfiguration {

	@Resource
	private org.springframework.core.env.Environment environment;

	@Bean
	public EmbeddedSolrServerFactoryBean solrServerFactoryBean() {
		final EmbeddedSolrServerFactoryBean factory = new EmbeddedSolrServerFactoryBean();

		factory.setSolrHome(environment.getRequiredProperty("solr.solr.home"));

		return factory;
	}

	@Bean
	public SolrTemplate solrTemplate() throws Exception {
		return new SolrTemplate(solrServerFactoryBean().getObject());
	}
}
