package models;

/**
 * Created by brianzhao on 1/7/17.
 */
public class ParkingLot {
    private final String name;
    private int numParked;
    private final int maxCapacity;

    public ParkingLot(String name, int numParked, int maxCapacity) {
        this.name = name;
        this.numParked = numParked;
        this.maxCapacity = maxCapacity;
    }

    public ParkingLot(ParkingLot other) {
        this.name = other.name;
        this.numParked = other.numParked;
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


    public int getNumParked() {
        return numParked;
    }


    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setNumParked(int numParked) {
        this.numParked = numParked;
    }

    @Override
    public String toString() {
        return "ParkingLot{" +
                "name='" + name + '\'' +
                ", numParked=" + numParked +
                ", maxCapacity=" + maxCapacity +
                '}';
    }
}
