//By student id: 38555093
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

class MemoryMgmt {
    private List<MemBlock> MEMORY;      //represents main virtual memory, starts at address 0. Is increased by SBRK
    private List<Integer[]> FREE_LIST;  //represents the list of free spaces within Memory, uses Best Fit (smallest -> largest) 
    private int maxSize;                //maximum size of virtual memory currently, used for base address calculations (mentioned later)
    

    MemoryMgmt(int maxSize) {
        MEMORY = new ArrayList<>();
        FREE_LIST = new ArrayList<>();
        MEMORY.add(new MemBlock(true, maxSize));
        
        //FREE_LIST[0] = location, FREE_LIST[1] = size, FREE_LIST[3] = base address of memory (prevents SBRK'd memory being merged into original memory)
        Integer[] tempArray = {0, maxSize, 0}; 
        FREE_LIST.add(tempArray);
        this.maxSize = maxSize;
    }

    //Testing method, called by main method
    public void print() { 

        
        MemoryMgmt test = new MemoryMgmt(8192);
        test.malloc(8192); 
        // System.out.println("\nTest 1:");
        // int pointer1 = test.malloc(28);
        // test.setMem("Hello", pointer1);
        // test.getMem(pointer1, pointer1+5);
        // test.free(pointer1);

        // System.out.println("\nTest 2:");
        // pointer1 = test.malloc(28);
        // int pointer2 = test.malloc(1024);
        // int pointer3 = test.malloc(28);
        // test.free(pointer2); 
        // pointer2 = test.malloc(512);
        // test.free(pointer1);
        // test.free(pointer2);
        // test.free(pointer3);

        // System.out.println("\nTest 3:");
        // pointer1 = test.malloc(7168);
        // pointer2 = test.malloc(1024);
        // test.free(pointer1);
        // test.free(pointer2);
        
        // System.out.println("\nTest 4:");
        // pointer1 = test.malloc(1024);
        // pointer2 = test.malloc(28);
        // test.free(pointer2);
        // test.free(pointer2);
     }

    //allocates size bytes of memory, returns the location in memory 
    public int malloc(int size) {
        //checks memory is of right size
        if (size < 1) {
            System.out.println("Cannot allocate this amount of memory");
            return -1;
        }
        Boolean found = false;          //used for sbrk
        int toAllocate = 0;             //stores pointer from freelist, or from sbrk 
        int freeListLocation = 0;       //stores pointer's index in free list
        System.out.print("Requesting " + size + "bytes of memory...");
        
        //searches through the freelist for free space
        for (Integer[] free : FREE_LIST) {
            if (free[1] >= size) {
                toAllocate = free[0];
                found = true;
                break;
            }
            freeListLocation++;
        }

        //calls to sbrk() if no memory of adequate size is found
        if (!found) {
            toAllocate = sbrk(size);
            System.out.print("Requesting " + size + "bytes of memory...");
        }
        
        int memoryLocation = 0;         //stores pointer's index in memory
        int currAddress = 0;            //stores the current memory address 
        int base = 0;                   //stores the base_address of the chunk of memory (important for freeing correctly) 
        Boolean toCheck;                //used to check if a free space is ready for removal 
        
        //searches through memory to find the pointer given by the freelist 
        for (MemBlock mem: MEMORY) {
            if (toAllocate == currAddress) { 
                base = mem.getbase();
                break;
            }
            currAddress = mem.getSize() + currAddress;
            memoryLocation++;
        }

        //allocates memory, then adjusts size indicator of the free block
        MEMORY.add(memoryLocation, new MemBlock(false, size, base));
        toCheck = MEMORY.get(memoryLocation+1).setSize(MEMORY.get(memoryLocation+1).getSize() - size);    //sets the size of memory location being used (in actual memory)
        
        //removes the freeblock if size is smaller than 0
        if (toCheck) {
            MEMORY.remove(memoryLocation+1);
        }

        //updates the freelist pointer and size 
        FREE_LIST.get(freeListLocation)[1] -= size;     //sets size of memory location being used (in free list)
        FREE_LIST.get(freeListLocation)[0] += size;     //sets location of memory location  (in free list)
        if (FREE_LIST.get(freeListLocation)[1] < 1) {
            FREE_LIST.remove(freeListLocation);         //removes memory location if all space is used up (in free list)
        }
        
        System.out.println("memory allocated");
        System.out.println("Pointer: " + currAddress);
        return currAddress;                            //returns pointer to address in memory
    }

    public int sbrk(int size) {
        System.out.println("Memory limit exceeded, requesting further memory blocks...success");
        //finds closest power of 2 to allocate
        int x = 2;
        while (x <= size) { x = x*2; }
        
        //allocates a new free block of memory 
        MEMORY.add(new MemBlock(true, x, maxSize));
        
        //adds the block of memory to the freelist
        int toReturn = maxSize;
        maxSize = maxSize + x;
        Integer[] tempArray = {toReturn, maxSize - toReturn, toReturn};
        FREE_LIST.add(tempArray);
        
        //returns address of new memory
        return toReturn;
    }

    //used to free a pointer's memory
    public void free(int pointer) {
        System.out.print("Freeing pointer " + pointer);
        int currAddress = 0;
        
        //similar to malloc, searches through list until at the right address
        for(MemBlock mem : MEMORY) {
            if (pointer == currAddress && !mem.getFree()) {
                mem.setFree(true);                  //sets the memory as free
                
                //updates the free list with new information. 
                int freeListLocation = 0;
                System.out.println("... memory freed.");
                
                //This ensures that blocks will be put in best fit. As memory is freed it is put in the correct position. I could have used
                //a sorting function to ensure best-fit is maintained from the beginning, but sorting everytime memory is freed seemed inefficient
                //(especially if you take into account the ineffiency of my method of coalescing best-fit) 
                for (Integer[] free : FREE_LIST) {
                    if (free[1] >= mem.size) {
                        break;
                    }
                    freeListLocation++;
                }
                Integer[] tempArray = {pointer, mem.getSize(), mem.getbase()};
                FREE_LIST.add(freeListLocation,tempArray);
                
                //coalesces adjacent free blocks within the main memory and freelist
                combine();
                combineFreeList();
                return;

            }
            currAddress += mem.getSize();
        }
        System.out.println("... exception encountered, exiting.");
    }

    //method used to coalesce different memory blocks, called combine because i couldn't spell coalesce. 
    //Only finishes when there is nothing left to combine
    private void combine() {
        MemBlock last = null;
        for (MemBlock mem : MEMORY) {
            if (last != null && mem.getFree() && last.getbase() == mem.getbase()) {
                mem.setSize(last.getSize() + mem.getSize());
                MEMORY.remove(last);
                combine();              //usually only combines once, but using recusion here handles edge cases quite well
                return;
            }
            else if (mem.getFree()) {
                last = mem;
            }
            else {
                last = null;
            }
        }
        return;
    }
    
    //combines freeblocks within the freelist. Getting off Arraylist should be in constant time so should not be that inefficient. 
    //downside of using best-fit as I have to compare every entry against every other entry
    private void combineFreeList(){
        for (int i = 0; i < FREE_LIST.size(); i++) {
            for (int j = 0; j < FREE_LIST.size(); j++) {
                if ( FREE_LIST.get(i)[0] < FREE_LIST.get(j)[0] && FREE_LIST.get(i)[0] + 
                FREE_LIST.get(i)[1] >= FREE_LIST.get(j)[0] && FREE_LIST.get(i)[2] == FREE_LIST.get(j)[2]) {
                    FREE_LIST.get(i)[1] += FREE_LIST.get(j)[1];
                    FREE_LIST.remove(j);
                    //java doesn't let you edit lists while you are traversing through them 
                    //recursion somewhat fixes this, as a new for loop is created instead of continuing through original loop. 
                    combineFreeList();      
                    return;
                }
            }
        }
        return;
    }

    //used to set the contents of memory, setter is the object that memory will be set to, pointer is the memory location
    public void setMem(Object setter, int pointer) {
        if (pointer > maxSize) {
            System.out.println("Exception: Segmenation Fault");
            return;
        }
        
        int memLocation = 0;
        for (MemBlock mem : MEMORY) {
            if (memLocation <= pointer && pointer < mem.getSize()) {
                if (mem.getFree()) {
                    System.out.println("Exception: Cannot access free memory");
                }
                pointer = pointer - memLocation;
                if (mem.setMem(setter, pointer)) { 
                    System.out.println("Set memory address: " + pointer + "");
                }
                return;
            }

            memLocation += mem.getSize();
        } 
    }

    //gets the contents of memory at a specified location
    public void getMem(int startPointer, int endPointer) {
        int memLocation = 0;
        while (startPointer < endPointer) {
            for (MemBlock mem : MEMORY) {
                if (memLocation <= startPointer && startPointer < mem.getSize()) {
                    if (mem.getFree()) {
                        System.out.println("Exception: Cannot access free memory");
                        return;
                    }
                    int temp = startPointer - memLocation;
                    mem.printMem(temp);
                    break;
                }
            memLocation = memLocation + mem.getSize();
            }
        startPointer++;
        }
    }

    void printMem() {
        int memLocation = 0;
        for (MemBlock mem : MEMORY) {
            System.out.println("Memory at: " + memLocation + ", free?: " + mem.getFree() + ", size: " + mem.getSize() + ", base: " + mem.getbase());
            memLocation = memLocation + mem.getSize();
        }
    }

    public static void main(String[] args) {
        MemoryMgmt test = new MemoryMgmt(8192);
        test.print();
    }


    //fairly basic class representing a 'block' of memory
    class MemBlock{
        private Boolean free;
        private int size;
        private final int baseAdress;
        private Object memoryContents[];            //each memory block has an array which actually represents the memory within, array can be populated with setMem() method

        public MemBlock(Boolean free, int size) {
            this.free = free;
            this.size = size;
            if (size > 0)
                memoryContents = new Object[size];
            baseAdress = 0;
        }

        public MemBlock(Boolean free, int size, int baseAdress) {
            this.free = free;
            this.size = size;
            this.baseAdress = baseAdress;
            if (size > 0)
                memoryContents = new Object[size];
        }
        public Boolean setSize(int s) {
            if (s < 1) {
                size = s;
                return true;
            }
            size = s;
            return false; 
        }

        public void setFree(Boolean f) {
            free = f;
        }

        public int getSize() {
            return size;
        }

        public Boolean getFree() {
            return free;
        }

        public int getbase() {
            return baseAdress;
        }

        public Boolean setMem(Object contents, int index) {
            //checks if contents are of right type, could use method overiding to seperate into different methods, but seemed like unnessary complication.
            //not the best implementation, as integers are not stored properly. Characters within java have a size of 2 bytes, but in C they are only 1, so 
            //i decided to give them a size of 1 byte, as it felt more in spirit of the coursework.
            if (contents instanceof Character)
                memoryContents[index] = contents;
            else if (contents instanceof String) {
                for (int i = index; i < ((String) contents).length(); i++) {
                    memoryContents[i] = ((String) contents).charAt(i);
                }
            }
            else if (contents instanceof Integer) {
                memoryContents[index] = contents;
                for (int i = index+1; i < index + 4; i++) {
                    memoryContents[i] = 00000000;
                }
            }
            else {
                System.out.println("Exception: This Type is not Accepted");
                return false;
            }
            return true;
        }

        public Object getMem(int index) {
            return memoryContents[index];
        }

        public void printMem(int index) {
            System.out.println("[" + memoryContents[index] + "]");
        }
    }   
}
