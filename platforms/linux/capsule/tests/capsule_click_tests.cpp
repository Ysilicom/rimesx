#include "capsule_click.hpp"

#include <iostream>

namespace {

int g_failures = 0;

void Expect(bool cond, const char* message) {
    if (!cond) {
        std::cerr << "FAIL: " << message << '\n';
        ++g_failures;
    }
}

}  // namespace

int main() {
    using rimes::capsule::ShouldActivateOnPress;

    Expect(!ShouldActivateOnPress(0, -1, 0, -1, false), "first press only selects");
    Expect(ShouldActivateOnPress(0, 0, 80, 0, false), "second press at 80 ms activates");
    Expect(ShouldActivateOnPress(0, 0, 150, 0, false), "second press at 150 ms activates");
    Expect(ShouldActivateOnPress(0, 0, 300, 0, false), "second press at 300 ms activates");
    Expect(ShouldActivateOnPress(0, 0, 400, 0, false), "second press at 400 ms still activates");
    Expect(!ShouldActivateOnPress(0, 0, 401, 0, false), "press after 400 ms does not activate");
    Expect(!ShouldActivateOnPress(1, 0, 80, 0, false), "a different card only selects");
    Expect(ShouldActivateOnPress(1, 0, 80, 0, true), "GDK_2BUTTON_PRESS always activates");
    Expect(ShouldActivateOnPress(0, 0, 20, 0, false), "20 ms QMP gap activates");

    if (g_failures != 0) {
        std::cerr << g_failures << " capsule click failures\n";
        return 1;
    }
    std::cout << "ok: capsule click\n";
    return 0;
}
