package com.goodforgoodbusiness.endpoint.reasoner;

import org.apache.jena.graph.Triple;

import com.goodforgoodbusiness.endpoint.graph.BaseGraph;
import com.goodforgoodbusiness.endpoint.processor.ImportProcessor;
import com.goodforgoodbusiness.shared.LogConfigurer;

public class Reasoning {
	public static void main(String[] args) throws Exception {
		LogConfigurer.init(Reasoning.class, "log4j.properties");
		
		var graph = new BaseGraph() {
			@Override
			public void add(Triple t) {
				System.out.println("ADD: " + t);
				super.add(t);
			}
		};
		
		var processor = new ImportProcessor(() -> graph);
		
		var stream = Reasoning.class.getResourceAsStream("./schema.ttl");
		processor.importStream(stream, "TURTLE");
		
//		System.out.println(graph.size());
		
		Materializer.apply(graph, new org.semanticweb.HermiT.ReasonerFactory());
		
//		System.out.println(graph.size());
	}
}