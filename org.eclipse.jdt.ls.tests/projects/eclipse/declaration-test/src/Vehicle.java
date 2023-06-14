public class Vehicle implements MovingObject{
    public int speed(){
        return 60;
    }

    public int distance(){
        return speed()*60;
    }

    public String model;
}