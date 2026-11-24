package absolute_in_doubt.trading_platform.getaway.dto;

public class CommonDtos {

    public record PageInput(
            Integer limit,
            Integer offset
    ) {
        public int getEffectiveLimit() {
            return limit != null && limit > 0 ? limit : 20;
        }

        public int getEffectiveOffset() {
            return offset != null && offset >= 0 ? offset : 0;
        }
    }
}
