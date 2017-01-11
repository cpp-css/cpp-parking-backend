package models;

/**
 * Model of parking lot
 * is schema of json in play config
 * also used as object in clientManager's state
 */
public class ParkingLot {
    private final String name;
    private int occupancy;
    private final int maxCapacity;

    public ParkingLot(String name, int occupancy, int maxCapacity) {
        this.name = name;
        this.occupancy = occupancy;
        this.maxCapacity = maxCapacity;
    }

    public ParkingLot(ParkingLot other) {
        this.name = other.name;
        this.occupancy = other.occupancy;
        this.maxCapacity = other.maxCapacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParkingLot that = (ParkingLot) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }


    public String getName() {
        return name;
    }


    public int getOccupancy() {
        return occupancy;
    }


    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setOccupancy(int occupancy) {
        this.occupancy = occupancy;
    }

    @Override
    public String toString() {
        return "ParkingLot{" +
                "name='" + name + '\'' +
                ", occupancy=" + occupancy +
                ", maxCapacity=" + maxCapacity +
                '}';
    }
}
