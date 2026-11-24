package absolute_in_doubt.trading_platform.getaway.config;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
public class GraphQLConfig {

    public static final GraphQLScalarType DATE_TIME = GraphQLScalarType.newScalar()
            .name("DateTime")
            .description("DateTime scalar representing an ISO-8601 string")
            .coercing(new StringCoercing())
            .build();

    public static final GraphQLScalarType DATE = GraphQLScalarType.newScalar()
            .name("Date")
            .description("Date scalar representing a YYYY-MM-DD string")
            .coercing(new StringCoercing())
            .build();

    public static final GraphQLScalarType DECIMAL = GraphQLScalarType.newScalar()
            .name("Decimal")
            .description("Decimal scalar representing arbitrary precision decimal string")
            .coercing(new StringCoercing())
            .build();

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(DATE_TIME)
                .scalar(DATE)
                .scalar(DECIMAL);
    }

    private static class StringCoercing implements Coercing<Object, String> {
        @Override
        public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
            return dataFetcherResult != null ? dataFetcherResult.toString() : null;
        }

        @Override
        public Object parseValue(Object input) throws CoercingParseValueException {
            return input != null ? input.toString() : null;
        }

        @Override
        public Object parseLiteral(Object input) throws CoercingParseLiteralException {
            if (input instanceof graphql.language.StringValue sv) {
                return sv.getValue();
            } else if (input instanceof graphql.language.IntValue iv) {
                return iv.getValue().toString();
            } else if (input instanceof graphql.language.FloatValue fv) {
                return fv.getValue().toString();
            }
            return input != null ? input.toString() : null;
        }
    }
}
