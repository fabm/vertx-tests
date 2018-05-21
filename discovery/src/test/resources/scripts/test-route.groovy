id = 'mock-unit-test-1'

routeJson('/my/url', [methods: ['get']]) {
    'a simple response'
}

routeJson('another') {
    [
            1: 'a simple string',
            2: true,
            3: 2,
            4: null,
            6: [
                    hello    : 'world',
                    nullInput: null
            ]
    ]
}