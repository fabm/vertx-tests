package pt.fabm

createDB = 'CREATE DATABASE STUDENTS'

createTableStudents = '''\
CREATE TABLE REGISTRATION
(id INTEGER not NULL,
first VARCHAR(255),
last VARCHAR(255),
age INTEGER,
PRIMARY KEY (id))'''

insertRows = [
        '''INSERT INTO Registration VALUES (100, 'Zara', 'Ali', 18)''',
        '''INSERT INTO Registration VALUES (101, 'Mahnaz', 'Fatma', 25)''',
        '''INSERT INTO Registration VALUES (102, 'Zaid', 'Khan', 30)''',
        '''INSERT INTO Registration VALUES (103, 'Sumit', 'Mittal', 28)''',
]

update = '''UPDATE Registration SET age = 30 WHERE id in (100, 101)'''

selectToCheckUpdate = '''SELECT id, first, last, age FROM Registration'''