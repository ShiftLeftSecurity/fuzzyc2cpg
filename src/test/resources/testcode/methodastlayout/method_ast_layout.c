void method1() {

}

void method2() {
  int local = 1;
}

void method3() {
  int x;
  int y;
  int z;

  x = y + z;
}

void method4() {
  int x;
  {
    int y;
  }
}

void method5(int x) {
  while (x < 1) {
    x += 1;
  }
}

void method6(int x) {
  int y;
  if (x > 0) {
    y = 0;
  }
}

void method7(int x) {
  int y;
  if (x > 0) {
    y = 0;
  } else {
    y = 1;
  }
}
