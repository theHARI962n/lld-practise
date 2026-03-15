import java.util.*;
import java.time.*;
import java.util.concurrent.*;

// ========== Enums ==========
enum VehicleType { MOTORCYCLE, CAR, TRUCK }
enum SpotType { COMPACT, REGULAR, LARGE }
enum PaymentStatus { PENDING, COMPLETED, FAILED }

// ========== Vehicle Classes ==========
abstract class Vehicle {
    private String licensePlate;
    private VehicleType type;
    private ParkingTicket ticket;

    public Vehicle(String plate, VehicleType type) {
        this.licensePlate = plate;
        this.type = type;
    }

    public VehicleType getType() { return type; }
    public String getLicensePlate() { return licensePlate; }
    public void assignTicket(ParkingTicket ticket) { this.ticket = ticket; }
    public ParkingTicket getTicket() { return ticket; }
}

class Motorcycle extends Vehicle {
    public Motorcycle(String plate) { super(plate, VehicleType.MOTORCYCLE); }
}

class Car extends Vehicle {
    public Car(String plate) { super(plate, VehicleType.CAR); }
}

class Truck extends Vehicle {
    public Truck(String plate) { super(plate, VehicleType.TRUCK); }
}

// ========== Parking Spot ==========
abstract class ParkingSpot {
    private String id;
    private boolean isFree = true;
    private Vehicle vehicle;
    private SpotType spotType;

    public ParkingSpot(String id, SpotType type) {
        this.id = id;
        this.spotType = type;
    }

    public boolean isFree() { return isFree; }
    public SpotType getType() { return spotType; }
    public String getId() { return id; }

    public boolean canFitVehicle(Vehicle v) {
        return isFree && isCompatible(v.getType());
    }

    // Template method - subclasses define compatibility
    protected abstract boolean isCompatible(VehicleType type);

    public synchronized void assignVehicle(Vehicle v) {
        if (!isFree) throw new IllegalStateException("Spot occupied");
        this.vehicle = v;
        this.isFree = false;
    }

    public synchronized Vehicle removeVehicle() {
        Vehicle v = this.vehicle;
        this.vehicle = null;
        this.isFree = true;
        return v;
    }
}

class CompactSpot extends ParkingSpot {
    public CompactSpot(String id) { super(id, SpotType.COMPACT); }

    @Override
    protected boolean isCompatible(VehicleType type) {
        return type == VehicleType.MOTORCYCLE || type == VehicleType.CAR;
    }
}

class RegularSpot extends ParkingSpot {
    public RegularSpot(String id) { super(id, SpotType.REGULAR); }

    @Override
    protected boolean isCompatible(VehicleType type) {
        return type != VehicleType.TRUCK;
    }
}

class LargeSpot extends ParkingSpot {
    public LargeSpot(String id) { super(id, SpotType.LARGE); }

    @Override
    protected boolean isCompatible(VehicleType type) {
        return true; // Can fit any vehicle
    }
}

// ========== Parking Ticket ==========
class ParkingTicket {
    private String ticketId;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private Vehicle vehicle;
    private ParkingSpot spot;
    private Map<SpotType, Double> hourlyRates;

    public ParkingTicket(Vehicle v, ParkingSpot spot, Map<SpotType, Double> rates) {
        this.ticketId = UUID.randomUUID().toString().substring(0, 8);
        this.entryTime = LocalDateTime.now();
        this.vehicle = v;
        this.spot = spot;
        this.hourlyRates = rates;
    }

    public String getTicketId() { return ticketId; }
    public ParkingSpot getSpot() { return spot; }

    public double calculateFee() {
        exitTime = LocalDateTime.now();
        long hours = Duration.between(entryTime, exitTime).toHours();
        if (hours == 0) hours = 1; // Minimum 1 hour
        double rate = hourlyRates.getOrDefault(spot.getType(), 5.0);
        return hours * rate;
    }
}

// ========== Parking Floor ==========
class ParkingFloor {
    private int floorNumber;
    private List<ParkingSpot> spots;

    public ParkingFloor(int number, int compact, int regular, int large) {
        this.floorNumber = number;
        this.spots = new ArrayList<>();

        for (int i = 0; i < compact; i++)
            spots.add(new CompactSpot("F" + number + "-C" + i));
        for (int i = 0; i < regular; i++)
            spots.add(new RegularSpot("F" + number + "-R" + i));
        for (int i = 0; i < large; i++)
            spots.add(new LargeSpot("F" + number + "-L" + i));
    }

    public synchronized ParkingSpot findAvailableSpot(Vehicle v) {
        // Try to find smallest compatible spot first (space optimization)
        for (ParkingSpot spot : spots) {
            if (spot.canFitVehicle(v)) {
                return spot;
            }
        }
        return null;
    }

    public int getAvailableCount() {
        return (int) spots.stream().filter(ParkingSpot::isFree).count();
    }

    public int getAvailableCount(SpotType type) {
        return (int) spots.stream()
                .filter(s -> s.isFree() && s.getType() == type)
                .count();
    }
}

// ========== Parking Lot (Singleton) ==========
class ParkingLot {
    private static volatile ParkingLot instance;
    private List<ParkingFloor> floors;
    private Map<SpotType, Double> hourlyRates;
    private Map<String, ParkingTicket> activeTickets;

    private ParkingLot() {
        floors = new ArrayList<>();
        hourlyRates = new HashMap<>();
        hourlyRates.put(SpotType.COMPACT, 3.0);
        hourlyRates.put(SpotType.REGULAR, 5.0);
        hourlyRates.put(SpotType.LARGE, 8.0);
        activeTickets = new ConcurrentHashMap<>();
    }

    // Thread-safe singleton with double-checked locking
    public static ParkingLot getInstance() {
        if (instance == null) {
            synchronized (ParkingLot.class) {
                if (instance == null) {
                    instance = new ParkingLot();
                }
            }
        }
        return instance;
    }

    public void addFloor(ParkingFloor floor) {
        floors.add(floor);
    }

    public synchronized ParkingTicket parkVehicle(Vehicle vehicle) {
        for (ParkingFloor floor : floors) {
            ParkingSpot spot = floor.findAvailableSpot(vehicle);
            if (spot != null) {
                spot.assignVehicle(vehicle);
                ParkingTicket ticket = new ParkingTicket(vehicle, spot, hourlyRates);
                vehicle.assignTicket(ticket);
                activeTickets.put(ticket.getTicketId(), ticket);
                System.out.println("Parked " + vehicle.getLicensePlate() +
                        " at " + spot.getId());
                return ticket;
            }
        }
        System.out.println("Parking lot full!");
        return null;
    }

    public double unparkVehicle(ParkingTicket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Invalid ticket");
        }
        double fee = ticket.calculateFee();
        ParkingSpot spot = ticket.getSpot();
        spot.removeVehicle();
        activeTickets.remove(ticket.getTicketId());
        System.out.println("Fee: $" + fee);
        return fee;
    }

    public void displayAvailability() {
        System.out.println("=== Parking Availability ===");
        for (int i = 0; i < floors.size(); i++) {
            ParkingFloor floor = floors.get(i);
            System.out.println("Floor " + i + ": " +
                    floor.getAvailableCount() + " spots available");
        }
    }

    public boolean isFull() {
        return floors.stream().allMatch(f -> f.getAvailableCount() == 0);
    }
}

// ========== Main / Demo ==========
public class ParkingLotDemo {
    public static void main(String[] args) {
        ParkingLot lot = ParkingLot.getInstance();
        lot.addFloor(new ParkingFloor(1, 5, 10, 2));
        lot.addFloor(new ParkingFloor(2, 5, 10, 2));

        Car car1 = new Car("ABC-123");
        Car car2 = new Car("XYZ-789");
        Motorcycle bike = new Motorcycle("BIKE-001");

        ParkingTicket t1 = lot.parkVehicle(car1);
        ParkingTicket t2 = lot.parkVehicle(car2);
        ParkingTicket t3 = lot.parkVehicle(bike);

        lot.displayAvailability();

        // Simulate time passing...
        double fee = lot.unparkVehicle(t1);
        System.out.println("Car 1 paid: $" + fee);
    }
}