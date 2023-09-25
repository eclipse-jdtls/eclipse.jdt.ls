package org.sample;

public class NestedSwitchFoldingRange {
    
	void testNoException()
	{
		if (true)
		{
		}
	}

    void foo(){

        int x = 1;
        int y = 2;

        switch(x) {
            case 1:
            {
                switch(y) {
                    case 1:
                        System.out.println(y);
                    case 2:
                        System.out.println(x);
                }
            }
            case 2:
            {
                System.out.println(x);
            }
        }
    }
}
